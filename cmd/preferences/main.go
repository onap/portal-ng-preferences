package main

import (
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	_ "github.com/jackc/pgx/v5/stdlib"
	"github.com/pressly/goose/v3"

	"github.com/onap/portal-ng/preferences/internal/config"
	"github.com/onap/portal-ng/preferences/internal/handler"
	"github.com/onap/portal-ng/preferences/internal/middleware"
	"github.com/onap/portal-ng/preferences/internal/repository"
	"github.com/onap/portal-ng/preferences/internal/service"
	"github.com/onap/portal-ng/preferences/internal/tracing"
	"github.com/onap/portal-ng/preferences/migrations"
)

var version = "0.3.1" // set via ldflags at build time

func main() {
	// Structured JSON logging
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	slog.SetDefault(logger)

	cfg := config.Load()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	// Initialize OTel tracing
	if cfg.TracingEnabled {
		shutdown, err := tracing.InitTracer(ctx, cfg.OTLPEndpoint, "portal-ng-preferences", version)
		if err != nil {
			slog.Error("failed to initialize tracing", "error", err)
		} else {
			defer shutdown(context.Background())
		}
	}

	// Database connection pool
	pool, err := pgxpool.New(ctx, cfg.DatabaseURL())
	if err != nil {
		slog.Error("failed to create database pool", "error", err)
		os.Exit(1)
	}
	defer pool.Close()

	if err := pool.Ping(ctx); err != nil {
		slog.Error("failed to ping database", "error", err)
		os.Exit(1)
	}
	slog.Info("database connection established")

	// Run migrations
	if err := runMigrations(cfg.DatabaseURL()); err != nil {
		slog.Error("failed to run migrations", "error", err)
		os.Exit(1)
	}

	// Wire up layers
	repo := repository.NewPostgresRepository(pool)
	svc := service.NewPreferencesService(repo)
	prefsHandler := handler.NewPreferencesHandler(svc)
	healthHandler := handler.NewHealthHandler(repo, version)

	// Build router
	mux := http.NewServeMux()

	// Actuator/health endpoints (no auth)
	mux.HandleFunc("GET /actuator/health", healthHandler.Health)
	mux.HandleFunc("GET /actuator/health/liveness", healthHandler.Health)
	mux.HandleFunc("GET /actuator/health/readiness", healthHandler.Readiness)
	mux.HandleFunc("GET /actuator/info", healthHandler.Info)

	// API endpoints (protected by JWT auth middleware via wrapper)
	mux.HandleFunc("GET /v1/preferences", prefsHandler.GetPreferences)
	mux.HandleFunc("POST /v1/preferences", prefsHandler.SavePreferences)
	mux.HandleFunc("PUT /v1/preferences", prefsHandler.UpdatePreferences)

	// Middleware chain
	jwtAuth := middleware.NewJWTAuth(cfg.JWKSURL(), cfg.LogExcludePaths)
	loggingCfg := middleware.LoggingConfig{
		Enabled:       cfg.LoggingEnabled,
		TraceIDHeader: cfg.TraceIDHeader,
		ExcludePaths:  cfg.LogExcludePaths,
	}

	var h http.Handler = mux
	h = jwtAuth.Middleware(h)
	h = middleware.Logging(loggingCfg)(h)
	if cfg.TracingEnabled {
		h = tracing.HTTPMiddleware("portal-ng-preferences")(h)
	}

	// Start server
	addr := fmt.Sprintf("0.0.0.0:%d", cfg.ServerPort)
	srv := &http.Server{
		Addr:         addr,
		Handler:      h,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		slog.Info("starting server", "addr", addr)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("server error", "error", err)
			os.Exit(1)
		}
	}()

	<-ctx.Done()
	slog.Info("shutting down server")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		slog.Error("server shutdown error", "error", err)
	}
	slog.Info("server stopped")
}

func runMigrations(databaseURL string) error {
	db, err := sql.Open("pgx", databaseURL)
	if err != nil {
		return fmt.Errorf("open db for migrations: %w", err)
	}
	defer db.Close()

	goose.SetBaseFS(migrations.FS)
	if err := goose.SetDialect("postgres"); err != nil {
		return fmt.Errorf("set goose dialect: %w", err)
	}

	if err := goose.Up(db, "."); err != nil {
		return fmt.Errorf("run migrations: %w", err)
	}

	slog.Info("database migrations applied successfully")
	return nil
}
