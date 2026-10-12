CREATE TABLE loyalty_point_check (
  character_id  INTEGER PRIMARY KEY REFERENCES characters(character_id) ON DELETE CASCADE,
  checked_at    TEXT NOT NULL
);

INSERT INTO loyalty_point_check(character_id, checked_at)
SELECT character_id, MAX(fetched_at) FROM character_loyalty_points GROUP BY character_id;

ALTER TABLE loyalty_point_history ADD COLUMN checked_since TEXT;
