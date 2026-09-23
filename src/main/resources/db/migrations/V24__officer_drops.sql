CREATE TABLE sde_item_type (
  type_id     INTEGER PRIMARY KEY,
  type_name   TEXT NOT NULL,
  is_officer  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_sde_item_type_name ON sde_item_type(type_name);

CREATE TABLE officer_drop (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  character_id   INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  officer_name   TEXT NOT NULL,
  first_seen_at  TEXT NOT NULL,
  type_id        INTEGER NOT NULL,
  type_name      TEXT NOT NULL,
  quantity       INTEGER NOT NULL,
  unit_price     REAL NOT NULL,
  added_at       TEXT NOT NULL
);

CREATE INDEX idx_officer_drop_sighting ON officer_drop(character_id, officer_name, first_seen_at);
