CREATE TABLE character_contract (
  character_id       INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  contract_id        INTEGER NOT NULL,
  type               TEXT,
  status             TEXT,
  title              TEXT,
  collateral         REAL,
  price              REAL,
  reward             REAL,
  volume             REAL,
  date_issued        TEXT,
  date_expired       TEXT,
  date_completed     TEXT,
  for_corporation    INTEGER NOT NULL,
  issuer_id          INTEGER,
  assignee_id        INTEGER,
  acceptor_id        INTEGER,
  start_location_id  INTEGER,
  end_location_id    INTEGER,
  PRIMARY KEY (character_id, contract_id)
);

CREATE INDEX idx_character_contract_char_issued ON character_contract(character_id, date_issued);
