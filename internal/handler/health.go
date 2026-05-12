// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package handler

import (
	"encoding/json"
	"net/http"

	"github.com/onap/portal-ng/preferences/internal/repository"
)

type HealthHandler struct {
	repo    repository.PreferencesRepository
	version string
}

func NewHealthHandler(repo repository.PreferencesRepository, version string) *HealthHandler {
	return &HealthHandler{repo: repo, version: version}
}

func (h *HealthHandler) Health(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "UP"})
}

func (h *HealthHandler) Readiness(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	if err := h.repo.Ping(r.Context()); err != nil {
		w.WriteHeader(http.StatusServiceUnavailable)
		json.NewEncoder(w).Encode(map[string]string{"status": "DOWN"})
		return
	}
	json.NewEncoder(w).Encode(map[string]string{"status": "UP"})
}

func (h *HealthHandler) Info(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	info := map[string]interface{}{
		"build": map[string]string{
			"artifact": "onap-portal-ng-preferences",
			"name":     "Portal-ng user preferences service",
			"version":  h.version,
		},
	}
	json.NewEncoder(w).Encode(info)
}
