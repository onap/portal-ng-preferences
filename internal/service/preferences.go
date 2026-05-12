package service

import (
	"context"
	"encoding/json"

	"github.com/onap/portal-ng/preferences/internal/repository"
)

type PreferencesService struct {
	repo repository.PreferencesRepository
}

func NewPreferencesService(repo repository.PreferencesRepository) *PreferencesService {
	return &PreferencesService{repo: repo}
}

func (s *PreferencesService) GetPreferences(ctx context.Context, userID string) (json.RawMessage, error) {
	pref, err := s.repo.Get(ctx, userID)
	if err != nil {
		return nil, err
	}
	return pref.Properties, nil
}

func (s *PreferencesService) SavePreferences(ctx context.Context, userID string, properties json.RawMessage) (json.RawMessage, error) {
	pref, err := s.repo.Save(ctx, userID, properties)
	if err != nil {
		return nil, err
	}
	return pref.Properties, nil
}
