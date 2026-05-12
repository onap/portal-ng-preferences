-- +goose Up
CREATE TABLE IF NOT EXISTS preferences (
    user_id TEXT PRIMARY KEY,
    properties JSONB
);

-- +goose Down
DROP TABLE IF EXISTS preferences;
