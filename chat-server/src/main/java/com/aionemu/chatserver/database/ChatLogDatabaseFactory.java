package com.aionemu.chatserver.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.chatserver.configs.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Dedicated connection pool for the chat log database. This is intentionally
 * separate from the main game/login database so chat history is stored in its
 * own schema (and can even live on a different MySQL instance).
 */
public final class ChatLogDatabaseFactory {

	private static final Logger log = LoggerFactory.getLogger(ChatLogDatabaseFactory.class);
	private static HikariDataSource pool;

	private ChatLogDatabaseFactory() {
	}

	public static synchronized void init() {
		if (pool != null)
			return;
		if (!Config.LOGDB_ENABLED) {
			log.info("Chat log DB is disabled (chatserver.logdb.enabled=false).");
			return;
		}

		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			throw new Error("MySQL JDBC driver not found", e);
		}

		String url = Config.LOGDB_URL;
		String user = Config.LOGDB_USER;
		String password = Config.LOGDB_PASSWORD;

		// Strip the query string (?useUnicode=...&characterEncoding=...) and the
		// database name, so we can connect to the server root to create the DB.
		String noQuery = url.split("\\?")[0];
		int slash = noQuery.lastIndexOf('/');
		String dbName = (slash >= 0 && slash + 1 < noQuery.length()) ? noQuery.substring(slash + 1) : "aion2_chat";
		String baseUrl = (slash >= 0) ? noQuery.substring(0, slash + 1) : noQuery;

		try (Connection root = DriverManager.getConnection(baseUrl, user, password);
				Statement st = root.createStatement()) {
			st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + dbName + "` DEFAULT CHARACTER SET utf8");
			log.info("Ensured chat log database '{}' exists.", dbName);
		} catch (SQLException e) {
			throw new Error("Failed to ensure chat log database '" + dbName + "'", e);
		}

		HikariConfig c = new HikariConfig();
		c.setJdbcUrl(url);
		c.setUsername(user);
		c.setPassword(password);
		c.setMinimumIdle(1);
		c.setMaximumPoolSize(5);
		c.setAutoCommit(true);
		c.setPoolName("ChatLogDB");
		pool = new HikariDataSource(c);

		createSchema();
		log.info("Chat log DB pool started: {}", url);
	}

	private static void createSchema() {
		try (Connection con = pool.getConnection(); Statement st = con.createStatement()) {
			st.executeUpdate(
					"CREATE TABLE IF NOT EXISTS chat_log (" +
							" id BIGINT NOT NULL AUTO_INCREMENT," +
							" player_id INT NOT NULL," +
							" player_name VARCHAR(64) NOT NULL DEFAULT ''," +
							" channel_type VARCHAR(16) NOT NULL," +
							" channel_id INT NOT NULL DEFAULT 0," +
							" message TEXT NOT NULL," +
							" created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
							" PRIMARY KEY (id)," +
							" KEY idx_created (created_at)," +
							" KEY idx_player (player_id)," +
							" KEY idx_name (player_name)," +
							" KEY idx_channel (channel_type)" +
							") ENGINE=InnoDB DEFAULT CHARSET=utf8");
		} catch (SQLException e) {
			log.error("Failed to create chat_log table", e);
		}

		// Migrate older tables that were created before player_name existed.
		try (Connection con = pool.getConnection(); Statement st = con.createStatement()) {
			st.executeUpdate("ALTER TABLE chat_log ADD COLUMN player_name VARCHAR(64) NOT NULL DEFAULT ''");
		} catch (SQLException e) {
			if (e.getErrorCode() != 1060) { // 1060 = ER_DUP_FIELDNAME (already exists)
				log.error("Failed to add player_name column to chat_log", e);
			}
		}
	}

	public static Connection getConnection() throws SQLException {
		if (pool == null)
			throw new SQLException("ChatLogDatabaseFactory not initialized");
		return pool.getConnection();
	}

	public static synchronized void shutdown() {
		if (pool != null) {
			pool.close();
			pool = null;
		}
	}
}
