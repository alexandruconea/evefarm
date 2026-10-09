CREATE TABLE order_competition (
  order_id      INTEGER PRIMARY KEY,
  character_id  INTEGER NOT NULL,
  best_price    REAL,
  outbid        INTEGER NOT NULL,
  checked_at    TEXT NOT NULL
);

CREATE INDEX idx_order_competition_character ON order_competition(character_id);
