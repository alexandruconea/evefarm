WITH settings AS (
  SELECT (SELECT CAST(value AS INTEGER) FROM app_settings
          WHERE key = 'lp_store_favorite_corporation_id' AND TRIM(value) <> '') AS corporation_id,
         COALESCE((SELECT CAST(value AS REAL) FROM app_settings
                   WHERE key = 'lp_store_target_isk_per_lp' AND TRIM(value) <> ''), 500) AS isk_per_lp
),
recalculated AS (
  SELECT s.id,
         COALESCE(
           (SELECT h.loyalty_points FROM loyalty_point_history h
            WHERE h.character_id = s.character_id AND h.corporation_id = settings.corporation_id
              AND julianday(h.recorded_at) <= julianday(s.captured_at)
            ORDER BY julianday(h.recorded_at) DESC LIMIT 1),
           (SELECT h.loyalty_points FROM loyalty_point_history h
            WHERE h.character_id = s.character_id AND h.corporation_id = settings.corporation_id
            ORDER BY julianday(h.recorded_at) LIMIT 1)
         ) * settings.isk_per_lp AS lp_value
  FROM tracker_snapshot s, settings
)
UPDATE tracker_snapshot
SET total_value = total_value - COALESCE(tracker_snapshot.lp_value, 0) + recalculated.lp_value,
    lp_value = recalculated.lp_value
FROM recalculated
WHERE recalculated.id = tracker_snapshot.id AND recalculated.lp_value IS NOT NULL;
