package com.aionemu.gameserver.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Asynchronous writer that stores every game-server chat message into the
 * dedicated chat log database (schema aion2_chat, table chat_log). The chat
 * server handles global channels (LFG, trade, ...) while the game server handles
 * local channels (normal, shout, group, alliance, legion, league, whisper); both
 * write into the same table so the web admin can show the full history.
 */
public final class ChatLogService {

	private static final Logger log = LoggerFactory.getLogger(ChatLogService.class);
	private static final String SCHEMA = "aion2_chat";
	private static final ChatLogService INSTANCE = new ChatLogService();

	private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "GameChatLogWriter");
		t.setDaemon(true);
		return t;
	});

	private ChatLogService() {
	}

	public static ChatLogService getInstance() {
		return INSTANCE;
	}

	public void log(Player player, ChatType type, String message) {
		if (player == null || message == null)
			return;
		final int playerId = player.getObjectId();
		final String name = player.getName();
		final String channel = (type != null) ? type.name() : "UNKNOWN";
		final String text = message;

		writer.submit(() -> {
			try (Connection con = DatabaseFactory.getConnection();
					PreparedStatement ps = con.prepareStatement(
							"INSERT INTO " + SCHEMA + ".chat_log (player_id, player_name, channel_type, channel_id, message) VALUES (?, ?, ?, 0, ?)")) {
				ps.setInt(1, playerId);
				ps.setString(2, name);
				ps.setString(3, channel);
				ps.setString(4, text);
				ps.executeUpdate();
			} catch (Exception e) {
				log.warn("Failed to write chat log entry", e);
			}
		});
	}

	public void shutdown() {
		writer.shutdown();
	}
}
