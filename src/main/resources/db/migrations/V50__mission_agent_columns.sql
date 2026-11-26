UPDATE table_column_state
SET columns_json = replace(columns_json, '"agent"', '"agent","corporation","faction"')
WHERE panel_key = 'missionAgents' AND columns_json NOT LIKE '%"corporation"%';
