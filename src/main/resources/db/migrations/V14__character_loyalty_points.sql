CREATE TABLE character_loyalty_points (
  character_id     INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  corporation_id   INTEGER NOT NULL,
  loyalty_points   INTEGER NOT NULL,
  fetched_at       TEXT NOT NULL,
  PRIMARY KEY (character_id, corporation_id)
);
