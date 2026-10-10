CREATE TABLE market_daily_volume (
  region_id     INTEGER NOT NULL,
  type_id       INTEGER NOT NULL,
  daily_volume  REAL NOT NULL,
  fetched_at    TEXT NOT NULL,
  PRIMARY KEY (region_id, type_id)
);

UPDATE table_column_state
SET columns_json = replace(columns_json, '"fivePercentVolume"', '"dailyVolume","daysToSell"')
WHERE panel_key = 'lpStore';
