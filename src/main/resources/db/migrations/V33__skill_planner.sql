CREATE TABLE sde_skill (
  type_id              INTEGER PRIMARY KEY,
  name                 TEXT NOT NULL,
  group_name           TEXT,
  description          TEXT,
  skill_rank           INTEGER NOT NULL,
  primary_attribute    TEXT,
  secondary_attribute  TEXT
);

CREATE TABLE sde_skill_requirement (
  type_id           INTEGER NOT NULL,
  required_type_id  INTEGER NOT NULL,
  required_level    INTEGER NOT NULL,
  PRIMARY KEY (type_id, required_type_id)
);

CREATE TABLE implant_attribute_bonus (
  type_id       INTEGER PRIMARY KEY,
  charisma      INTEGER NOT NULL,
  intelligence  INTEGER NOT NULL,
  memory        INTEGER NOT NULL,
  perception    INTEGER NOT NULL,
  willpower     INTEGER NOT NULL,
  cached_at     TEXT NOT NULL
);

CREATE TABLE character_skill (
  character_id   INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  skill_id       INTEGER NOT NULL,
  skillpoints    INTEGER NOT NULL,
  trained_level  INTEGER NOT NULL,
  active_level   INTEGER NOT NULL,
  PRIMARY KEY (character_id, skill_id)
);

CREATE TABLE character_attributes (
  character_id         INTEGER PRIMARY KEY REFERENCES characters(character_id) ON DELETE CASCADE,
  charisma             INTEGER NOT NULL,
  intelligence         INTEGER NOT NULL,
  memory               INTEGER NOT NULL,
  perception           INTEGER NOT NULL,
  willpower            INTEGER NOT NULL,
  implant_ids          TEXT NOT NULL,
  total_sp             INTEGER NOT NULL,
  unallocated_sp       INTEGER NOT NULL,
  bonus_remaps         INTEGER,
  last_remap_date      TEXT,
  remap_cooldown_date  TEXT,
  fetched_at           TEXT NOT NULL
);

CREATE TABLE skill_plan (
  plan_id       INTEGER PRIMARY KEY AUTOINCREMENT,
  character_id  INTEGER NOT NULL REFERENCES characters(character_id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  created_at    TEXT NOT NULL,
  UNIQUE (character_id, name)
);

CREATE TABLE skill_plan_entry (
  plan_id   INTEGER NOT NULL REFERENCES skill_plan(plan_id) ON DELETE CASCADE,
  position  INTEGER NOT NULL,
  skill_id  INTEGER NOT NULL,
  level     INTEGER NOT NULL,
  planned   INTEGER NOT NULL,
  notes     TEXT,
  PRIMARY KEY (plan_id, skill_id, level)
);
