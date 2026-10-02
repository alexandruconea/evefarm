CREATE TABLE character_standings (
  character_id  INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  from_id       INTEGER NOT NULL,
  from_type     TEXT NOT NULL,
  standing      REAL NOT NULL,
  fetched_at    TEXT NOT NULL,
  PRIMARY KEY (character_id, from_id)
);

CREATE INDEX idx_sde_agent_corporation ON sde_agent(corporation_id);
