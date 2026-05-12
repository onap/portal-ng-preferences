// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

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
	"github.com/onap/portal-ng/preferences/internal/model"
	"github.com/onap/portal-ng/preferences/internal/repository"
	"github.com/onap/portal-ng/preferences/internal/service"
	"github.com/onap/portal-ng/preferences/internal/tracing"
	"github.com/onap/portal-ng/preferences/migrations"
)

var version = "0.3.1"

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	slog.SetDefault(logger)

	cfg := config.Load()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	if cfg.TracingEnabled {
		shutdown, err := tracing.InitTracer(ctx, cfg.OTLPEndpoint, "portal-ng-preferences", version)
		if err != nil {
			slog.Error("failed to initialize tracing", "error", err)
		} else {
			defer shutdown(context.Background())
		}
	}

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

	if err := runMigrations(cfg.DatabaseURL()); err != nil {
		slog.Error("failed to run migrations", "error", err)
		os.Exit(1)
	}

	repo := repository.NewPostgresRepository(pool)
	svc := service.NewPreferencesService(repo)
	prefsHandler := handler.NewPreferencesHandler(svc)
	healthHandler := handler.NewHealthHandler(repo, version)

	mux := http.NewServeMux()

	// Health endpoints (no auth)
	mux.HandleFunc("GET /actuator/health", healthHandler.Health)
	mux.HandleFunc("GET /actuator/health/liveness", healthHandler.Health)
	mux.HandleFunc("GET /actuator/health/readiness", healthHandler.Readiness)
	mux.HandleFunc("GET /actuator/info", healthHandler.Info)

	// API routes via generated OpenAPI handler
	model.HandlerFromMux(prefsHandler, mux)

	// Middleware chain
	jwtAuth, err := middleware.NewJWTAuth(cfg.IssuerURL(), cfg.LogExcludePaths)
	if err != nil {
		slog.Error("failed to initialize JWT auth", "error", err)
		os.Exit(1)
	}

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
