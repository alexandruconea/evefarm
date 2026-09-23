CREATE TABLE wallet_journal_entry (
  character_id     INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  entry_id         INTEGER NOT NULL,
  date             TEXT NOT NULL,
  ref_type         TEXT,
  amount           REAL,
  balance          REAL,
  description      TEXT,
  reason           TEXT,
  first_party_id   INTEGER,
  second_party_id  INTEGER,
  tax              REAL,
  tax_receiver_id  INTEGER,
  PRIMARY KEY (character_id, entry_id)
);

CREATE INDEX idx_wallet_journal_char_date ON wallet_journal_entry(character_id, date);

CREATE TABLE entity_name_cache (
  entity_id  INTEGER PRIMARY KEY,
  name       TEXT NOT NULL,
  category   TEXT,
  cached_at  TEXT NOT NULL
);
