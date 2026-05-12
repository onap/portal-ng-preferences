// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package handler

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"

	"github.com/onap/portal-ng/preferences/internal/httputil"
	"github.com/onap/portal-ng/preferences/internal/middleware"
	"github.com/onap/portal-ng/preferences/internal/model"
	"github.com/onap/portal-ng/preferences/internal/service"
)

type PreferencesHandler struct {
	svc *service.PreferencesService
}

func NewPreferencesHandler(svc *service.PreferencesService) *PreferencesHandler {
	return &PreferencesHandler{svc: svc}
}

func (h *PreferencesHandler) GetPreferences(w http.ResponseWriter, r *http.Request) {
	userID, ok := r.Context().Value(middleware.UserIDKey).(string)
	if !ok || userID == "" {
		httputil.WriteProblem(w, http.StatusUnauthorized, "Missing user identity")
		return
	}

	props, err := h.svc.GetPreferences(r.Context(), userID)
	if err != nil {
		slog.Error("failed to get preferences", "user_id", userID, "error", err)
		httputil.WriteProblem(w, http.StatusInternalServerError, "Failed to retrieve preferences")
		return
	}

	writePreferences(w, props)
}

func (h *PreferencesHandler) SavePreferences(w http.ResponseWriter, r *http.Request) {
	userID, ok := r.Context().Value(middleware.UserIDKey).(string)
	if !ok || userID == "" {
		httputil.WriteProblem(w, http.StatusUnauthorized, "Missing user identity")
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, 1<<20) // 1MB limit
	body, err := io.ReadAll(r.Body)
	if err != nil {
		httputil.WriteProblem(w, http.StatusBadRequest, "Failed to read request body")
		return
	}

	var req model.Preferences
	if err := json.Unmarshal(body, &req); err != nil {
		httputil.WriteProblem(w, http.StatusBadRequest, "Invalid JSON body")
		return
	}

	propsJSON, err := json.Marshal(req.Properties)
	if err != nil {
		httputil.WriteProblem(w, http.StatusBadRequest, "Invalid properties")
		return
	}

	props, err := h.svc.SavePreferences(r.Context(), userID, propsJSON)
	if err != nil {
		slog.Error("failed to save preferences", "user_id", userID, "error", err)
		httputil.WriteProblem(w, http.StatusInternalServerError, "Failed to save preferences")
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
