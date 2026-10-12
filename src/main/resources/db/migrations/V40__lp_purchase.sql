CREATE TABLE lp_purchase (
  purchase_id       INTEGER PRIMARY KEY AUTOINCREMENT,
  character_id      INTEGER NOT NULL,
  corporation_id    INTEGER NOT NULL,
  corporation_name  TEXT NOT NULL,
  offer_id          INTEGER NOT NULL,
  type_id           INTEGER NOT NULL,
  type_name         TEXT NOT NULL,
  offers            INTEGER NOT NULL,
  units             INTEGER NOT NULL,
  lp_spent          INTEGER NOT NULL,
  isk_spent         REAL NOT NULL,
  other_cost        REAL NOT NULL,
  target_isk_per_lp REAL,
  purchased_at      TEXT NOT NULL,
  journal_entry_id  INTEGER,
  used_units        INTEGER NOT NULL DEFAULT 0,
  notes             TEXT
);

CREATE INDEX idx_lp_purchase_type ON lp_purchase(type_id);

CREATE TABLE lp_payment_ignored (
  entry_id  INTEGER PRIMARY KEY
);
