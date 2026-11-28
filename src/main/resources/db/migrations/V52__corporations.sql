CREATE TABLE corporation (
  corporation_id INTEGER PRIMARY KEY,
  name           TEXT NOT NULL,
  ticker         TEXT
);

CREATE TABLE corporation_access (
  character_id   INTEGER PRIMARY KEY REFERENCES characters(character_id) ON DELETE CASCADE,
  corporation_id INTEGER NOT NULL,
  roles          TEXT NOT NULL,
  checked_at     TEXT NOT NULL
);

CREATE TABLE corporation_division (
  corporation_id INTEGER NOT NULL,
  kind           TEXT NOT NULL,
  division       INTEGER NOT NULL,
  name           TEXT,
  PRIMARY KEY (corporation_id, kind, division)
);

CREATE TABLE corporation_wallet (
  corporation_id INTEGER NOT NULL,
  division       INTEGER NOT NULL,
  balance        REAL NOT NULL,
  updated_at     TEXT NOT NULL,
  PRIMARY KEY (corporation_id, division)
);

CREATE TABLE corporation_journal (
  corporation_id  INTEGER NOT NULL,
  division        INTEGER NOT NULL,
  entry_id        INTEGER NOT NULL,
  date            TEXT NOT NULL,
  ref_type        TEXT,
  amount          REAL NOT NULL,
  balance         REAL NOT NULL,
  description     TEXT,
  reason          TEXT,
  first_party_id  INTEGER,
  second_party_id INTEGER,
  PRIMARY KEY (corporation_id, division, entry_id)
);

CREATE INDEX idx_corporation_journal_date ON corporation_journal(corporation_id, date);

CREATE TABLE corporation_transaction (
  corporation_id INTEGER NOT NULL,
  division       INTEGER NOT NULL,
  transaction_id INTEGER NOT NULL,
  date           TEXT NOT NULL,
  type_id        INTEGER NOT NULL,
  quantity       INTEGER NOT NULL,
  price          REAL NOT NULL,
  client_id      INTEGER,
  location_id    INTEGER,
  is_buy         INTEGER NOT NULL,
  journal_ref_id INTEGER,
  PRIMARY KEY (corporation_id, division, transaction_id)
);

CREATE TABLE corporation_asset (
  corporation_id    INTEGER NOT NULL,
  item_id           INTEGER NOT NULL,
  type_id           INTEGER NOT NULL,
  quantity          INTEGER NOT NULL,
  location_id       INTEGER,
  location_flag     TEXT,
  is_singleton      INTEGER NOT NULL,
  is_blueprint_copy INTEGER NOT NULL,
  name              TEXT,
  container_name    TEXT,
  unit_price        REAL NOT NULL,
  total_value       REAL NOT NULL,
  fetched_at        TEXT NOT NULL,
  PRIMARY KEY (corporation_id, item_id)
);
