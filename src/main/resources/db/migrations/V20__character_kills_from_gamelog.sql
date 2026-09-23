DROP TABLE IF EXISTS character_kills;

CREATE TABLE character_kills (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  character_id  INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  killed_at     TEXT NOT NULL,
  npc_name      TEXT NOT NULL,
  faction_label TEXT NOT NULL,
  UNIQUE(character_id, killed_at, npc_name)
);

CREATE INDEX idx_character_kills_char_time ON character_kills(character_id, killed_at);
CREATE INDEX idx_character_kills_faction ON character_kills(faction_label);

CREATE TABLE kill_log_progress (
  file_name  TEXT PRIMARY KEY,
  last_size  INTEGER NOT NULL
);
