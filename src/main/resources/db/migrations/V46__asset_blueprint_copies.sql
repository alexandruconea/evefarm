ALTER TABLE asset_current ADD COLUMN is_blueprint_copy INTEGER NOT NULL DEFAULT 0;
ALTER TABLE asset_archive ADD COLUMN is_blueprint_copy INTEGER NOT NULL DEFAULT 0;
