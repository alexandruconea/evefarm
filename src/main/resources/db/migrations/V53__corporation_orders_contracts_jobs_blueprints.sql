CREATE TABLE corporation_market_order (
  corporation_id  INTEGER NOT NULL,
  order_id        INTEGER NOT NULL,
  wallet_division INTEGER,
  type_id         INTEGER NOT NULL,
  is_buy_order    INTEGER NOT NULL,
  price           REAL NOT NULL,
  volume_remain   INTEGER NOT NULL,
  volume_total    INTEGER NOT NULL,
  escrow          REAL,
  location_id     INTEGER,
  issued          TEXT,
  duration        INTEGER,
  state           TEXT,
  range           TEXT,
  min_volume      INTEGER,
  fetched_at      TEXT NOT NULL,
  PRIMARY KEY (corporation_id, order_id)
);

CREATE TABLE corporation_contract (
  corporation_id    INTEGER NOT NULL,
  contract_id       INTEGER NOT NULL,
  type              TEXT,
  status            TEXT,
  title             TEXT,
  collateral        REAL,
  price             REAL,
  reward            REAL,
  volume            REAL,
  date_issued       TEXT,
  date_expired      TEXT,
  date_completed    TEXT,
  issuer_id         INTEGER,
  assignee_id       INTEGER,
  acceptor_id       INTEGER,
  start_location_id INTEGER,
  end_location_id   INTEGER,
  items_status      TEXT,
  PRIMARY KEY (corporation_id, contract_id)
);

CREATE TABLE corporation_contract_item (
  corporation_id INTEGER NOT NULL,
  contract_id    INTEGER NOT NULL,
  record_id      INTEGER NOT NULL,
  type_id        INTEGER NOT NULL,
  quantity       INTEGER NOT NULL,
  raw_quantity   INTEGER,
  is_included    INTEGER NOT NULL,
  PRIMARY KEY (corporation_id, contract_id, record_id)
);

CREATE TABLE corporation_industry_job (
  corporation_id     INTEGER NOT NULL,
  job_id             INTEGER NOT NULL,
  installer_id       INTEGER,
  activity_id        INTEGER NOT NULL,
  status             TEXT,
  blueprint_type_id  INTEGER NOT NULL,
  product_type_id    INTEGER,
  runs               INTEGER,
  cost               REAL,
  facility_id        INTEGER,
  output_location_id INTEGER,
  start_date         TEXT,
  end_date           TEXT,
  PRIMARY KEY (corporation_id, job_id)
);

CREATE TABLE corporation_blueprint (
  corporation_id      INTEGER NOT NULL,
  item_id             INTEGER NOT NULL,
  type_id             INTEGER NOT NULL,
  location_id         INTEGER,
  location_flag       TEXT,
  quantity            INTEGER NOT NULL,
  material_efficiency INTEGER NOT NULL,
  time_efficiency     INTEGER NOT NULL,
  runs                INTEGER NOT NULL,
  PRIMARY KEY (corporation_id, item_id)
);
