-- Ranked PvP Battleground: rating table + player command registration.

CREATE TABLE IF NOT EXISTS `ranked_bg_rating` (
  `player_id` INT UNSIGNED NOT NULL,
  `format`    TINYINT NOT NULL COMMENT 'team size (1..6)',
  `rating`    SMALLINT NOT NULL DEFAULT 1000,
  `wins`      INT NOT NULL DEFAULT 0,
  `losses`    INT NOT NULL DEFAULT 0,
  `points`    INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`player_id`, `format`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Register the player command (security 0 = available to all players).
-- Adjust the `command` table structure if your schema differs (needs name, security, help columns).
INSERT IGNORE INTO `command` (`name`, `security`, `help`)
VALUES ('rankedbg', 0, 'Ranked battleground: join <1-6> | leave | rating <1-6> | top <1-6>');
