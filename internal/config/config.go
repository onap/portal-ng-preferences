// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package config

import (
	"fmt"
	"net/url"
	"os"
	"strconv"
)

type Config struct {
	ServerPort int

	KeycloakURL   string
	KeycloakRealm string

	DBHost     string
	DBPort     int
	DBName     string
	DBUser     string
	DBPassword string

	OTLPEndpoint    string
	TracingEnabled  bool
	TraceIDHeader   string
	LoggingEnabled  bool
	LogExcludePaths []string
}

func (c *Config) IssuerURL() string {
	return fmt.Sprintf("%s/realms/%s", c.KeycloakURL, c.KeycloakRealm)
}

func (c *Config) DatabaseURL() string {
	return fmt.Sprintf("postgres://%s:%s@%s:%d/%s?sslmode=disable",
		url.QueryEscape(c.DBUser), url.QueryEscape(c.DBPassword), c.DBHost, c.DBPort, c.DBName)
}

func Load() *Config {
	return &Config{
		ServerPort: envInt("SERVER_PORT", 9001),

		KeycloakURL:   envStr("KEYCLOAK_URL", "http://localhost:8080"),
		KeycloakRealm: envStr("KEYCLOAK_REALM", "ONAP"),

		DBHost:     envStr("PREFERENCES_DB_HOST", "localhost"),
		DBPort:     envInt("PREFERENCES_DB_PORT", 5432),
		DBName:     envStr("PREFERENCES_DB", "preferences"),
		DBUser:     envStr("PREFERENCES_DB_USERNAME", "postgres"),
		DBPassword: envStr("PREFERENCES_DB_PASSWORD", "postgres"),

		OTLPEndpoint:   envStr("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318"),
		TracingEnabled: envBool("TRACING_ENABLED", true),
		TraceIDHeader:  envStr("TRACE_ID_HEADER_NAME", "x-b3-traceid"),

		LoggingEnabled:  envBool("LOGGING_ENABLED", true),
		LogExcludePaths: []string{"/actuator/"},
	}
}

func envStr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func envInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return fallback
}

func envBool(key string, fallback bool) bool {
	if v := os.Getenv(key); v != "" {
		if b, err := strconv.ParseBool(v); err == nil {
			return b
		}
	}
	return fallback
}
