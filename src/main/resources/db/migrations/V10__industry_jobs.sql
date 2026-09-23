CREATE TABLE industry_job (
  character_id        INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  job_id              INTEGER NOT NULL,
  activity_id         INTEGER NOT NULL,
  status              TEXT,
  blueprint_type_id   INTEGER,
  product_type_id     INTEGER,
  runs                INTEGER,
  cost                REAL,
  facility_id         INTEGER,
  output_location_id  INTEGER,
  start_date          TEXT,
  end_date            TEXT,
  PRIMARY KEY (character_id, job_id)
);

CREATE INDEX idx_industry_job_char_end ON industry_job(character_id, end_date);
