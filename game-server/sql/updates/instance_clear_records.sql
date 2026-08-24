-- Speedrun / dungeon clear-time records.
-- Stores the best (fastest) clear time per player per instance (dungeon).
-- The game-server also auto-creates this table on first use (CREATE TABLE IF NOT EXISTS),
-- but this script is provided for manual deployment.

CREATE TABLE IF NOT EXISTS `instance_clear_records` (
  `player_id`      INT          NOT NULL,
  `map_id`        INT          NOT NULL,
  `player_name`   VARCHAR(64)  NOT NULL,
  `best_time_ms`  BIGINT       NOT NULL,
  `clear_count`   INT          NOT NULL DEFAULT 1,
  `last_clear_time` TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`player_id`, `map_id`),
  KEY `idx_map_time` (`map_id`, `best_time_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
