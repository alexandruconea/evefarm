ALTER TABLE tokens ADD COLUMN status TEXT NOT NULL DEFAULT 'active';
UPDATE tokens SET access_token = NULL, access_token_expires_at = NULL;
