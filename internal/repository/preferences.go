package repository

import (
	"context"
	"encoding/json"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Preferences struct {
	UserID     string
	Properties json.RawMessage
}

type PreferencesRepository interface {
	Get(ctx context.Context, userID string) (*Preferences, error)
	Save(ctx context.Context, userID string, properties json.RawMessage) (*Preferences, error)
	Ping(ctx context.Context) error
}

type postgresRepo struct {
	pool *pgxpool.Pool
}

func NewPostgresRepository(pool *pgxpool.Pool) PreferencesRepository {
	return &postgresRepo{pool: pool}
}

func (r *postgresRepo) Get(ctx context.Context, userID string) (*Preferences, error) {
	var props json.RawMessage
	err := r.pool.QueryRow(ctx,
		"SELECT properties FROM preferences WHERE user_id = $1", userID,
	).Scan(&props)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return &Preferences{UserID: userID, Properties: nil}, nil
		}
		return nil, err
	}
	return &Preferences{UserID: userID, Properties: props}, nil
}

func (r *postgresRepo) Save(ctx context.Context, userID string, properties json.RawMessage) (*Preferences, error) {
	_, err := r.pool.Exec(ctx,
		`INSERT INTO preferences (user_id, properties) VALUES ($1, $2)
		 ON CONFLICT (user_id) DO UPDATE SET properties = EXCLUDED.properties`,
		userID, properties,
	)
	if err != nil {
		return nil, err
	}
	return &Preferences{UserID: userID, Properties: properties}, nil
}

func (r *postgresRepo) Ping(ctx context.Context) error {
	return r.pool.Ping(ctx)
}
