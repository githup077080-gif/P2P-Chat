CREATE TABLE IF NOT EXISTS bots (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    handle TEXT UNIQUE NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    system_prompt TEXT NOT NULL DEFAULT '',
    welcome_message TEXT NOT NULL DEFAULT '',
    commands_json TEXT NOT NULL DEFAULT '[]',
    auto_reply INTEGER NOT NULL DEFAULT 1,
    webhook_url TEXT,
    api_token TEXT NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 0,
    messages_handled INTEGER NOT NULL DEFAULT 0,
    activated_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_bots_owner_id ON bots (owner_id, id);
