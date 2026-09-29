UPDATE market_order_current SET state = 'active' WHERE state IS NULL;

CREATE INDEX idx_market_order_char_state ON market_order_current(character_id, state);

ALTER TABLE character_contract ADD COLUMN items_status TEXT;

CREATE TABLE character_contract_item (
  character_id  INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  contract_id   INTEGER NOT NULL,
  record_id     INTEGER NOT NULL,
  type_id       INTEGER NOT NULL,
  quantity      INTEGER NOT NULL,
  raw_quantity  INTEGER,
  is_included   INTEGER NOT NULL,
  PRIMARY KEY (character_id, contract_id, record_id)
);

CREATE TABLE loyalty_point_history (
  character_id    INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  corporation_id  INTEGER NOT NULL,
  loyalty_points  INTEGER NOT NULL,
  recorded_at     TEXT NOT NULL,
  PRIMARY KEY (character_id, corporation_id, recorded_at)
);

INSERT INTO loyalty_point_history(character_id, corporation_id, loyalty_points, recorded_at)
SELECT character_id, corporation_id, loyalty_points, fetched_at FROM character_loyalty_points;

CREATE TABLE asset_archive (
  character_id    INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  month           TEXT NOT NULL,
  captured_at     TEXT NOT NULL,
  item_id         INTEGER NOT NULL,
  type_id         INTEGER NOT NULL,
  quantity        INTEGER NOT NULL,
  location_id     INTEGER,
  location_flag   TEXT,
  is_singleton    INTEGER,
  name            TEXT,
  container_name  TEXT,
  unit_price      REAL,
  total_value     REAL,
  PRIMARY KEY (character_id, month, item_id)
);

INSERT INTO asset_archive(character_id, month, captured_at, item_id, type_id, quantity, location_id, location_flag,
                          is_singleton, name, container_name, unit_price, total_value)
SELECT character_id, substr(fetched_at, 1, 7), fetched_at, item_id, type_id, quantity, location_id, location_flag,
       is_singleton, name, container_name, unit_price, total_value
FROM asset_current;
