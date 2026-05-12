// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package middleware

import (
	"context"
	"log/slog"
	"net/http"
	"strings"

	"github.com/coreos/go-oidc/v3/oidc"

	"github.com/onap/portal-ng/preferences/internal/httputil"
)

const UserIDKey contextKey = "userID"

type JWTAuth struct {
	verifier     *oidc.IDTokenVerifier
	excludePaths []string
}

func NewJWTAuth(issuerURL string, excludePaths []string) (*JWTAuth, error) {
	provider, err := oidc.NewProvider(context.Background(), issuerURL)
	if err != nil {
		return nil, err
	}
	verifier := provider.Verifier(&oidc.Config{
		SkipClientIDCheck: true,
	})
	return &JWTAuth{verifier: verifier, excludePaths: excludePaths}, nil
}

func (a *JWTAuth) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if isExcluded(r.URL.Path, a.excludePaths) {
			next.ServeHTTP(w, r)
			return
		}

		tokenStr := extractBearerToken(r)
		if tokenStr == "" {
			httputil.WriteProblem(w, http.StatusUnauthorized, "Missing or invalid Authorization header")
			return
		}

		idToken, err := a.verifier.Verify(r.Context(), tokenStr)
		if err != nil {
			slog.Warn("JWT validation failed", "error", err)
			httputil.WriteProblem(w, http.StatusUnauthorized, "Invalid token")
			return
		}

		ctx := context.WithValue(r.Context(), UserIDKey, idToken.Subject)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func extractBearerToken(r *http.Request) string {
	auth := r.Header.Get("Authorization")
	if !strings.HasPrefix(auth, "Bearer ") {
		return ""
	}
	return strings.TrimPrefix(auth, "Bearer ")
}
