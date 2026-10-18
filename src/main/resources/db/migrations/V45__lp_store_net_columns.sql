UPDATE table_column_state
SET columns_json = replace(replace(replace(replace(columns_json,
      '"iskPerLpSell"', '"iskPerLpSell","netIskPerLpSell"'),
      '"iskPerLpBuy"', '"iskPerLpBuy","netIskPerLpBuy"'),
      '"profitSell"', '"profitSell","netProfitSell"'),
      '"profitBuy"', '"profitBuy","netProfitBuy"')
WHERE panel_key = 'lpStore' AND columns_json NOT LIKE '%"net%';
