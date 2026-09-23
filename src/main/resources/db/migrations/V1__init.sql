CREATE TABLE app_settings (
  key   TEXT PRIMARY KEY,
  value TEXT
);

CREATE TABLE characters (
  character_id     INTEGER PRIMARY KEY,
  character_name   TEXT NOT NULL,
  corporation_id   INTEGER,
  scopes           TEXT,
  added_at         TEXT NOT NULL,
  enabled          INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE tokens (
  character_id             INTEGER PRIMARY KEY REFERENCES characters(character_id) ON DELETE CASCADE,
  refresh_token            TEXT NOT NULL,
  access_token             TEXT,
  access_token_expires_at  TEXT,
  updated_at               TEXT NOT NULL
);

CREATE TABLE type_cache (
  type_id       INTEGER PRIMARY KEY,
  name          TEXT NOT NULL,
  group_id      INTEGER,
  group_name    TEXT,
  category_id   INTEGER,
  category_name TEXT,
  volume        REAL,
  cached_at     TEXT NOT NULL
);

CREATE TABLE location_cache (
  location_id    INTEGER PRIMARY KEY,
  name           TEXT,
  location_type  TEXT,
  system_id      INTEGER,
  cached_at      TEXT NOT NULL
);

CREATE TABLE price_cache (
  type_id        INTEGER PRIMARY KEY,
  average_price  REAL,
  adjusted_price REAL,
  updated_at     TEXT NOT NULL
);

CREATE TABLE asset_current (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  character_id  INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  item_id       INTEGER NOT NULL,
  type_id       INTEGER NOT NULL,
  quantity      INTEGER NOT NULL,
  location_id   INTEGER,
  location_flag TEXT,
  is_singleton  INTEGER,
  name          TEXT,
  unit_price    REAL,
  total_value   REAL,
  fetched_at    TEXT NOT NULL,
  UNIQUE(character_id, item_id)
);

CREATE INDEX idx_asset_current_character ON asset_current(character_id);

CREATE TABLE tracker_snapshot (
  id                         INTEGER PRIMARY KEY AUTOINCREMENT,
  character_id               INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  captured_at                 TEXT NOT NULL,
  wallet_balance              REAL,
  assets_value                REAL,
  implants_value              REAL,
  sell_orders_value           REAL,
  escrow_value                REAL,
  escrow_to_cover_value       REAL,
  manufacturing_value         REAL,
  contract_collateral_value   REAL,
  contracts_value             REAL,
  skill_points                INTEGER,
  total_value                 REAL,
  UNIQUE(character_id, captured_at)
);

CREATE INDEX idx_tracker_snapshot_char_time ON tracker_snapshot(character_id, captured_at);
