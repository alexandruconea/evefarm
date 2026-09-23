CREATE TABLE sde_npc_type (
  type_id     INTEGER PRIMARY KEY,
  type_name   TEXT NOT NULL,
  group_id    INTEGER NOT NULL,
  group_name  TEXT NOT NULL
);

CREATE INDEX idx_sde_npc_type_name ON sde_npc_type(type_name);

ALTER TABLE character_kills ADD COLUMN bounty REAL;

CREATE TABLE combat_encounter (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  character_id  INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  log_file      TEXT NOT NULL,
  started_at    TEXT NOT NULL,
  ended_at      TEXT NOT NULL,
  solar_system  TEXT
);

CREATE INDEX idx_combat_encounter_file ON combat_encounter(log_file);
CREATE INDEX idx_combat_encounter_char_time ON combat_encounter(character_id, started_at);

CREATE TABLE combat_encounter_npc (
  encounter_id   INTEGER NOT NULL REFERENCES combat_encounter(id) ON DELETE CASCADE,
  npc_name       TEXT NOT NULL,
  first_seen_at  TEXT NOT NULL,
  last_seen_at   TEXT NOT NULL,
  kills          INTEGER NOT NULL,
  bounty         REAL NOT NULL,
  last_kill_at   TEXT,
  damage_dealt   INTEGER NOT NULL,
  damage_taken   INTEGER NOT NULL,
  PRIMARY KEY (encounter_id, npc_name)
);

CREATE INDEX idx_combat_encounter_npc_name ON combat_encounter_npc(npc_name);

CREATE TABLE officer_sighting (
  character_id   INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  officer_name   TEXT NOT NULL,
  first_seen_at  TEXT NOT NULL,
  belt           TEXT,
  notes          TEXT,
  payout_at      TEXT,
  payout_amount  REAL,
  payout_reason  TEXT,
  payout_description TEXT,
  PRIMARY KEY (character_id, officer_name, first_seen_at)
);

CREATE TABLE asteroid_belt_cache (
  solar_system  TEXT NOT NULL,
  belt_id       INTEGER NOT NULL,
  belt_name     TEXT NOT NULL,
  PRIMARY KEY (solar_system, belt_id)
);
