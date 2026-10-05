CREATE TABLE sde_industry_type (
  type_id        INTEGER PRIMARY KEY,
  name           TEXT NOT NULL,
  group_name     TEXT,
  category_name  TEXT,
  published      INTEGER NOT NULL
);

CREATE TABLE sde_industry_activity (
  blueprint_id  INTEGER NOT NULL,
  activity_id   INTEGER NOT NULL,
  time          INTEGER NOT NULL,
  PRIMARY KEY (blueprint_id, activity_id)
);

CREATE TABLE sde_industry_material (
  blueprint_id  INTEGER NOT NULL,
  activity_id   INTEGER NOT NULL,
  material_id   INTEGER NOT NULL,
  quantity      INTEGER NOT NULL,
  PRIMARY KEY (blueprint_id, activity_id, material_id)
);

CREATE TABLE sde_industry_product (
  blueprint_id  INTEGER NOT NULL,
  activity_id   INTEGER NOT NULL,
  product_id    INTEGER NOT NULL,
  quantity      INTEGER NOT NULL,
  probability   REAL,
  PRIMARY KEY (blueprint_id, activity_id, product_id)
);

CREATE INDEX idx_sde_industry_product_product ON sde_industry_product(product_id, activity_id);

CREATE TABLE sde_industry_skill (
  blueprint_id  INTEGER NOT NULL,
  activity_id   INTEGER NOT NULL,
  skill_id      INTEGER NOT NULL,
  level         INTEGER NOT NULL,
  PRIMARY KEY (blueprint_id, activity_id, skill_id)
);

CREATE TABLE sde_decryptor (
  type_id                 INTEGER PRIMARY KEY,
  name                    TEXT NOT NULL,
  probability_multiplier  REAL NOT NULL,
  me_modifier             INTEGER NOT NULL,
  te_modifier             INTEGER NOT NULL,
  runs_modifier           INTEGER NOT NULL
);
