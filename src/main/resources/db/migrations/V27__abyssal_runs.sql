CREATE TABLE abyssal_run (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  character_id     INTEGER NOT NULL,
  started_at       TEXT NOT NULL,
  duration_seconds INTEGER,
  tier             INTEGER,
  weather          TEXT,
  ship_type_id     INTEGER,
  ship_name        TEXT,
  survived         INTEGER NOT NULL DEFAULT 1,
  loot_value       REAL NOT NULL DEFAULT 0,
  filament_cost    REAL,
  notes            TEXT
);

CREATE INDEX idx_abyssal_run_started ON abyssal_run(started_at);

CREATE TABLE abyssal_run_loot (
  run_id     INTEGER NOT NULL REFERENCES abyssal_run(id) ON DELETE CASCADE,
  type_id    INTEGER NOT NULL,
  type_name  TEXT NOT NULL,
  quantity   INTEGER NOT NULL,
  unit_price REAL,
  PRIMARY KEY (run_id, type_id)
);
