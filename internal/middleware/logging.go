package middleware

import (
	"context"
	"log/slog"
	"net/http"
	"strings"
	"time"
)

type contextKey string

const TraceIDKey contextKey = "traceID"

type LoggingConfig struct {
	Enabled       bool
	TraceIDHeader string
	ExcludePaths  []string
}

func Logging(cfg LoggingConfig) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if !cfg.Enabled || isExcluded(r.URL.Path, cfg.ExcludePaths) {
				next.ServeHTTP(w, r)
				return
			}

			traceID := r.Header.Get(cfg.TraceIDHeader)
			ctx := context.WithValue(r.Context(), TraceIDKey, traceID)
			r = r.WithContext(ctx)

			start := time.Now()
			rw := &responseWriter{ResponseWriter: w, statusCode: http.StatusOK}

			slog.InfoContext(ctx, "RECEIVED",
				"trace_id", traceID,
				"method", r.Method,
				"url", r.URL.Path,
			)

			next.ServeHTTP(rw, r)

			duration := time.Since(start)
			slog.InfoContext(ctx, "FINISHED",
				"trace_id", traceID,
				"method", r.Method,
				"url", r.URL.Path,
				"status", rw.statusCode,
				"duration_ms", duration.Milliseconds(),
			)
		})
	}
}

func isExcluded(path string, patterns []string) bool {
	for _, p := range patterns {
		prefix := strings.TrimSuffix(p, "**")
		prefix = strings.TrimSuffix(prefix, "*")
		if strings.HasPrefix(path, prefix) {
			return true
		}
	}
	return false
}

type responseWriter struct {
	http.ResponseWriter
	statusCode int
}

func (rw *responseWriter) WriteHeader(code int) {
	rw.statusCode = code
	rw.ResponseWriter.WriteHeader(code)
}
