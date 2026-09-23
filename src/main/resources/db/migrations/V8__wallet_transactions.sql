CREATE TABLE wallet_transaction (
  character_id    INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  transaction_id  INTEGER NOT NULL,
  date            TEXT NOT NULL,
  type_id         INTEGER NOT NULL,
  quantity        INTEGER NOT NULL,
  price           REAL NOT NULL,
  client_id       INTEGER,
  location_id     INTEGER,
  is_buy          INTEGER NOT NULL,
  is_personal     INTEGER NOT NULL,
  journal_ref_id  INTEGER,
  PRIMARY KEY (character_id, transaction_id)
);

CREATE INDEX idx_wallet_transaction_char_date ON wallet_transaction(character_id, date);
