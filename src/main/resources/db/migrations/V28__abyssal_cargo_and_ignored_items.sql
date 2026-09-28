CREATE TABLE abyssal_run_cargo (
  run_id       INTEGER PRIMARY KEY REFERENCES abyssal_run(id) ON DELETE CASCADE,
  cargo_before TEXT,
  cargo_after  TEXT
);

CREATE TABLE abyssal_ignored_item (
  type_id   INTEGER PRIMARY KEY,
  type_name TEXT NOT NULL
);
