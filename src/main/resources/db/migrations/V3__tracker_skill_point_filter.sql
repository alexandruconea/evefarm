CREATE TABLE tracker_skill_point_filter (
  character_id INTEGER PRIMARY KEY REFERENCES characters(character_id) ON DELETE CASCADE,
  enabled      INTEGER NOT NULL DEFAULT 1,
  minimum_sp   INTEGER NOT NULL DEFAULT 0
);
