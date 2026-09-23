CREATE TABLE saved_table_filter (
  panel_key   TEXT NOT NULL,
  name        TEXT NOT NULL,
  conditions  TEXT NOT NULL,
  PRIMARY KEY (panel_key, name)
);
