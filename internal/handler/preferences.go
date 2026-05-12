package handler

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"

	"github.com/onap/portal-ng/preferences/internal/middleware"
	"github.com/onap/portal-ng/preferences/internal/service"
)

type PreferencesHandler struct {
	svc *service.PreferencesService
}

func NewPreferencesHandler(svc *service.PreferencesService) *PreferencesHandler {
	return &PreferencesHandler{svc: svc}
}

func (h *PreferencesHandler) GetPreferences(w http.ResponseWriter, r *http.Request) {
	userID := r.Context().Value(middleware.UserIDKey).(string)

	props, err := h.svc.GetPreferences(r.Context(), userID)
	if err != nil {
		slog.Error("failed to get preferences", "user_id", userID, "error", err)
		writeProblem(w, http.StatusInternalServerError, "Failed to retrieve preferences")
		return
	}

	writePreferences(w, props)
}

func (h *PreferencesHandler) SavePreferences(w http.ResponseWriter, r *http.Request) {
	userID := r.Context().Value(middleware.UserIDKey).(string)

	body, err := io.ReadAll(r.Body)
	if err != nil {
		writeProblem(w, http.StatusBadRequest, "Failed to read request body")
		return
	}

	var req struct {
		Properties json.RawMessage `json:"properties"`
	}
	if err := json.Unmarshal(body, &req); err != nil {
		writeProblem(w, http.StatusBadRequest, "Invalid JSON body")
		return
	}

	props, err := h.svc.SavePreferences(r.Context(), userID, req.Properties)
	if err != nil {
		slog.Error("failed to save preferences", "user_id", userID, "error", err)
		writeProblem(w, http.StatusInternalServerError, "Failed to save preferences")
		return
	}

	writePreferences(w, props)
}

func (h *PreferencesHandler) UpdatePreferences(w http.ResponseWriter, r *http.Request) {
	h.SavePreferences(w, r)
}

func writePreferences(w http.ResponseWriter, properties json.RawMessage) {
	w.Header().Set("Content-Type", "application/json")
	resp := map[string]json.RawMessage{"properties": properties}
	json.NewEncoder(w).Encode(resp)
}

func writeProblem(w http.ResponseWriter, status int, detail string) {
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(status)
	problem := map[string]interface{}{
		"type":   "about:blank",
		"title":  http.StatusText(status),
		"status": status,
		"detail": detail,
	}
	json.NewEncoder(w).Encode(problem)
}
