-- Class Duel ranked battleground: separate rating table + player command registration.

CREATE TABLE IF NOT EXISTS `ranked_bg_class_rating` (
  `player_id` INT UNSIGNED NOT NULL,
  `rating`    SMALLINT NOT NULL DEFAULT 1000,
  `wins`      INT NOT NULL DEFAULT 0,
  `losses`    INT NOT NULL DEFAULT 0,
  `points`    INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`player_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Register the class-duel player command (security 0 = available to all players).
INSERT IGNORE INTO `command` (`name`, `security`, `help`)
VALUES ('classbg', 0, 'Class duel: join | leave | rating | top');
