package integration_test

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/onap/portal-ng/preferences/internal/handler"
	"github.com/onap/portal-ng/preferences/internal/middleware"
	"github.com/onap/portal-ng/preferences/internal/repository"
	"github.com/onap/portal-ng/preferences/internal/service"
)

func setupTestDB(t *testing.T) (*pgxpool.Pool, func()) {
	t.Helper()
	ctx := context.Background()

	req := testcontainers.ContainerRequest{
		Image:        "postgres:16-alpine",
		ExposedPorts: []string{"5432/tcp"},
		Env: map[string]string{
			"POSTGRES_USER":     "test",
			"POSTGRES_PASSWORD": "test",
			"POSTGRES_DB":       "preferences",
		},
		WaitingFor: wait.ForLog("database system is ready to accept connections").
			WithOccurrence(2).
			WithStartupTimeout(30 * time.Second),
	}

	container, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: req,
		Started:          true,
	})
	if err != nil {
		t.Fatalf("failed to start postgres container: %v", err)
	}

	host, _ := container.Host(ctx)
	port, _ := container.MappedPort(ctx, "5432")

	dsn := fmt.Sprintf("postgres://test:test@%s:%s/preferences?sslmode=disable", host, port.Port())

	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Fatalf("failed to connect to test db: %v", err)
	}

	// Create table
	_, err = pool.Exec(ctx, `CREATE TABLE IF NOT EXISTS preferences (user_id TEXT PRIMARY KEY, properties JSONB)`)
	if err != nil {
		t.Fatalf("failed to create table: %v", err)
	}

	cleanup := func() {
		pool.Close()
		container.Terminate(ctx)
	}

	return pool, cleanup
}

func setupServer(pool *pgxpool.Pool) http.Handler {
	repo := repository.NewPostgresRepository(pool)
	svc := service.NewPreferencesService(repo)
	prefsHandler := handler.NewPreferencesHandler(svc)
	healthHandler := handler.NewHealthHandler(repo, "test")

	mux := http.NewServeMux()
	mux.HandleFunc("GET /actuator/health", healthHandler.Health)
	mux.HandleFunc("GET /actuator/health/readiness", healthHandler.Readiness)
	mux.HandleFunc("GET /actuator/info", healthHandler.Info)
	mux.HandleFunc("GET /v1/preferences", prefsHandler.GetPreferences)
	mux.HandleFunc("POST /v1/preferences", prefsHandler.SavePreferences)
	mux.HandleFunc("PUT /v1/preferences", prefsHandler.UpdatePreferences)

	return mux
}

// withUserID injects user ID into request context, simulating authenticated requests.
func withUserID(r *http.Request, userID string) *http.Request {
	ctx := context.WithValue(r.Context(), middleware.UserIDKey, userID)
	return r.WithContext(ctx)
}

func TestHealthEndpoint(t *testing.T) {
	pool, cleanup := setupTestDB(t)
	defer cleanup()

	srv := setupServer(pool)
	req := httptest.NewRequest(http.MethodGet, "/actuator/health", nil)
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}

	var resp map[string]string
	json.NewDecoder(w.Body).Decode(&resp)
	if resp["status"] != "UP" {
		t.Fatalf("expected status UP, got %s", resp["status"])
	}
}

func TestGetDefaultPreferences(t *testing.T) {
	pool, cleanup := setupTestDB(t)
	defer cleanup()

	srv := setupServer(pool)
	req := httptest.NewRequest(http.MethodGet, "/v1/preferences", nil)
	req = withUserID(req, "test-user")
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}

	body, _ := io.ReadAll(w.Body)
	var resp map[string]interface{}
	json.Unmarshal(body, &resp)
	if resp["properties"] != nil {
		t.Fatalf("expected null properties for new user, got %v", resp["properties"])
	}
}

func TestSaveAndGetPreferences(t *testing.T) {
	pool, cleanup := setupTestDB(t)
	defer cleanup()

	srv := setupServer(pool)

	// Save preferences
	payload := `{"properties":{"appStarter":"appStarterValue"}}`
	req := httptest.NewRequest(http.MethodPost, "/v1/preferences", strings.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	req = withUserID(req, "test-user")
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("POST expected 200, got %d: %s", w.Code, w.Body.String())
	}

	// Verify saved response
	body, _ := io.ReadAll(w.Body)
	var saveResp map[string]interface{}
	json.Unmarshal(body, &saveResp)
	props, ok := saveResp["properties"].(map[string]interface{})
	if !ok {
		t.Fatalf("expected properties map, got %T", saveResp["properties"])
	}
	if props["appStarter"] != "appStarterValue" {
		t.Fatalf("expected appStarterValue, got %v", props["appStarter"])
	}

	// Get preferences
	req = httptest.NewRequest(http.MethodGet, "/v1/preferences", nil)
	req = withUserID(req, "test-user")
	w = httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("GET expected 200, got %d", w.Code)
	}

	body, _ = io.ReadAll(w.Body)
	var getResp map[string]interface{}
	json.Unmarshal(body, &getResp)
	props, ok = getResp["properties"].(map[string]interface{})
	if !ok {
		t.Fatalf("expected properties map, got %T", getResp["properties"])
	}
	if props["appStarter"] != "appStarterValue" {
		t.Fatalf("expected appStarterValue, got %v", props["appStarter"])
	}
}

func TestUpdatePreferences(t *testing.T) {
	pool, cleanup := setupTestDB(t)
	defer cleanup()

	srv := setupServer(pool)

	// Save initial
	payload := `{"properties":{"key":"value1"}}`
	req := httptest.NewRequest(http.MethodPost, "/v1/preferences", strings.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	req = withUserID(req, "test-user")
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("POST expected 200, got %d", w.Code)
	}

	// Update via PUT
	payload = `{"properties":{"key":"value2","extra":"data"}}`
	req = httptest.NewRequest(http.MethodPut, "/v1/preferences", strings.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	req = withUserID(req, "test-user")
	w = httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("PUT expected 200, got %d: %s", w.Code, w.Body.String())
	}

	body, _ := io.ReadAll(w.Body)
	var resp map[string]interface{}
	json.Unmarshal(body, &resp)
	props := resp["properties"].(map[string]interface{})
	if props["key"] != "value2" {
		t.Fatalf("expected value2, got %v", props["key"])
	}
	if props["extra"] != "data" {
		t.Fatalf("expected data, got %v", props["extra"])
	}
}

func TestComplexNestedPreferences(t *testing.T) {
	pool, cleanup := setupTestDB(t)
	defer cleanup()

	srv := setupServer(pool)

	payload := `{"properties":{"appStarter":"appStarterValue1","dashboard":{"dashboardKey":"dashboardValue"}}}`
	req := httptest.NewRequest(http.MethodPost, "/v1/preferences", strings.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	req = withUserID(req, "test-user")
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("POST expected 200, got %d", w.Code)
	}

	// Retrieve and verify nested structure
	req = httptest.NewRequest(http.MethodGet, "/v1/preferences", nil)
	req = withUserID(req, "test-user")
	w = httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	body, _ := io.ReadAll(w.Body)
	var resp map[string]interface{}
	json.Unmarshal(body, &resp)
	props := resp["properties"].(map[string]interface{})
	dashboard := props["dashboard"].(map[string]interface{})
	if dashboard["dashboardKey"] != "dashboardValue" {
		t.Fatalf("expected dashboardValue, got %v", dashboard["dashboardKey"])
	}
}

func TestInfoEndpoint(t *testing.T) {
	pool, cleanup := setupTestDB(t)
	defer cleanup()

	srv := setupServer(pool)
	req := httptest.NewRequest(http.MethodGet, "/actuator/info", nil)
	w := httptest.NewRecorder()
	srv.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}

	var resp map[string]interface{}
	json.NewDecoder(w.Body).Decode(&resp)
	build := resp["build"].(map[string]interface{})
	if build["version"] != "test" {
		t.Fatalf("expected version 'test', got %v", build["version"])
	}
}
