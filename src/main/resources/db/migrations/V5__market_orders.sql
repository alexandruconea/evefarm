CREATE TABLE market_order_current (
  character_id   INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  order_id       INTEGER NOT NULL,
  type_id        INTEGER NOT NULL,
  is_buy_order   INTEGER NOT NULL,
  price          REAL NOT NULL,
  volume_remain  INTEGER NOT NULL,
  volume_total   INTEGER NOT NULL,
  escrow         REAL,
  location_id    INTEGER,
  issued         TEXT,
  duration       INTEGER,
  state          TEXT,
  fetched_at     TEXT NOT NULL,
  PRIMARY KEY (character_id, order_id)
);
