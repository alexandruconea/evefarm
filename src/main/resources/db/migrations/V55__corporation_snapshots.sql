ALTER TABLE corporation_contract ADD COLUMN for_corporation INTEGER;

CREATE TABLE corporation_snapshot (
  corporation_id            INTEGER NOT NULL,
  day                       TEXT NOT NULL,
  captured_at               TEXT NOT NULL,
  wallet_balance            REAL NOT NULL,
  assets_value              REAL NOT NULL,
  sell_orders_value         REAL NOT NULL,
  escrow_value              REAL NOT NULL,
  escrow_to_cover_value     REAL NOT NULL,
  manufacturing_value       REAL NOT NULL,
  contract_collateral_value REAL NOT NULL,
  contracts_value           REAL NOT NULL,
  total_value               REAL NOT NULL,
  PRIMARY KEY (corporation_id, day)
);

CREATE INDEX idx_corporation_snapshot_time ON corporation_snapshot(captured_at);
