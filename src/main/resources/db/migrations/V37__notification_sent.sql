CREATE TABLE notification_sent (
  kind          TEXT NOT NULL,
  character_id  INTEGER NOT NULL,
  item_key      TEXT NOT NULL,
  sent_at       TEXT NOT NULL,
  PRIMARY KEY (kind, character_id, item_key)
);
