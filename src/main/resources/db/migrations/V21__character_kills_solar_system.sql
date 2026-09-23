ALTER TABLE character_kills ADD COLUMN solar_system TEXT;
ALTER TABLE kill_log_progress ADD COLUMN parser_version INTEGER NOT NULL DEFAULT 0;
