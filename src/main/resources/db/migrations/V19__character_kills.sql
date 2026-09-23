CREATE TABLE character_kills (
  id                   INTEGER PRIMARY KEY AUTOINCREMENT,
  character_id         INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  killmail_id          INTEGER NOT NULL,
  killmail_hash        TEXT NOT NULL,
  killed_at            TEXT NOT NULL,
  victim_faction_id    INTEGER,
  victim_ship_type_id  INTEGER,
  solar_system_id      INTEGER,
  UNIQUE(character_id, killmail_id)
);

CREATE INDEX idx_character_kills_char_time ON character_kills(character_id, killed_at);
CREATE INDEX idx_character_kills_faction ON character_kills(victim_faction_id);
