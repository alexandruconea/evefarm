CREATE TABLE sde_accelerator (
  type_id         INTEGER PRIMARY KEY,
  name            TEXT NOT NULL,
  bonus           INTEGER NOT NULL,
  duration_hours  REAL NOT NULL
);

CREATE TABLE character_accelerator (
  character_id  INTEGER PRIMARY KEY REFERENCES characters(character_id) ON DELETE CASCADE,
  type_id       INTEGER,
  name          TEXT,
  bonus         INTEGER NOT NULL,
  first_seen    TEXT NOT NULL,
  ends_at       TEXT,
  set_by_user   INTEGER NOT NULL
);
