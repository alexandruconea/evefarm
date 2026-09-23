CREATE TABLE sde_agent (
  agent_id           INTEGER PRIMARY KEY,
  agent_name         TEXT NOT NULL,
  corporation_id     INTEGER,
  corporation_name   TEXT,
  faction_id         INTEGER,
  faction_name       TEXT,
  division_name      TEXT,
  agent_type_name    TEXT,
  level              INTEGER,
  is_locator         INTEGER NOT NULL,
  station_id         INTEGER,
  station_name       TEXT,
  solar_system_name  TEXT,
  security           REAL,
  constellation_name TEXT,
  region_name        TEXT
);
CREATE INDEX idx_sde_agent_name ON sde_agent(agent_name);
