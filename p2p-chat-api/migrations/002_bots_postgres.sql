CREATE TABLE IF NOT EXISTS bots (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    handle TEXT UNIQUE NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    system_prompt TEXT NOT NULL DEFAULT '',
    welcome_message TEXT NOT NULL DEFAULT '',
    commands_json TEXT NOT NULL DEFAULT '[]',
    auto_reply BOOLEAN NOT NULL DEFAULT TRUE,
    webhook_url TEXT,
    api_token TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    messages_handled BIGINT NOT NULL DEFAULT 0,
    activated_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bots_owner_id ON bots (owner_id, id);
