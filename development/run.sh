#!/usr/bin/env bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d

cd "$SCRIPT_DIR/.."
KEYCLOAK_URL=http://localhost:8080 \
KEYCLOAK_REALM=ONAP \
PREFERENCES_DB_HOST=localhost \
PREFERENCES_DB_PORT=5432 \
PREFERENCES_DB=preferences \
PREFERENCES_DB_USERNAME=postgres \
PREFERENCES_DB_PASSWORD=postgres \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 \
TRACE_ID_HEADER_NAME=x-b3-traceid \
go run ./cmd/preferences
