CREATE TABLE character_blueprint (
  character_id        INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  item_id             INTEGER NOT NULL,
  type_id             INTEGER NOT NULL,
  location_id         INTEGER,
  location_flag       TEXT,
  quantity            INTEGER NOT NULL,
  material_efficiency INTEGER NOT NULL,
  time_efficiency     INTEGER NOT NULL,
  runs                INTEGER NOT NULL,
  PRIMARY KEY (character_id, item_id)
);
