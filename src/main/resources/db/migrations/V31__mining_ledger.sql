CREATE TABLE mining_ledger (
  character_id     INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  date             TEXT NOT NULL,
  solar_system_id  INTEGER NOT NULL,
  type_id          INTEGER NOT NULL,
  quantity         INTEGER NOT NULL,
  updated_at       TEXT NOT NULL,
  PRIMARY KEY (character_id, date, solar_system_id, type_id)
);

CREATE INDEX idx_mining_ledger_date ON mining_ledger(date);

CREATE TABLE sde_ore (
  type_id             INTEGER PRIMARY KEY,
  portion_size        INTEGER NOT NULL,
  compressed_type_id  INTEGER
);

CREATE TABLE sde_ore_material (
  type_id           INTEGER NOT NULL,
  material_type_id  INTEGER NOT NULL,
  quantity          INTEGER NOT NULL,
  PRIMARY KEY (type_id, material_type_id)
);
