UPDATE table_column_state
SET columns_json = CASE
      WHEN columns_json LIKE '%"item"%' THEN replace(columns_json, '"item"', '"icon","item"')
      WHEN columns_json = '[]' THEN '["icon"]'
      ELSE '["icon",' || substr(columns_json, 2)
    END
WHERE panel_key IN ('lpHoldings', 'lpLots') AND columns_json NOT LIKE '%"icon"%';
