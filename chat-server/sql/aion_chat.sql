-- Dedicated chat log database (separate from login/game DBs).
-- The chat server auto-creates this database + table on startup when
-- chatserver.logdb.enabled=true, and the web server also ensures the table
-- via ensureSchema(). This file is provided for manual setup / reference.

CREATE DATABASE IF NOT EXISTS aion2_chat DEFAULT CHARACTER SET utf8;
USE aion2_chat;

CREATE TABLE IF NOT EXISTS chat_log (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  player_id    INT NOT NULL,
  channel_type VARCHAR(16) NOT NULL,
  channel_id   INT NOT NULL DEFAULT 0,
  message      TEXT NOT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_created (created_at),
  KEY idx_player (player_id),
  KEY idx_channel (channel_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
