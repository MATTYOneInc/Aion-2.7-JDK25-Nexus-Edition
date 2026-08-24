package com.aionemu.chatserver.service;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.chatserver.configs.Config;
import com.aionemu.chatserver.database.ChatLogDatabaseFactory;
import com.aionemu.chatserver.model.channel.Channel;
import com.aionemu.chatserver.model.message.Message;

/**
 * Asynchronous writer that stores every chat message into the dedicated chat
 * log database. Writing happens on a single daemon thread backed by a HikariCP
 * pool so it never blocks the Netty IO threads.
 */
public final class ChatLogService {

	private static final Logger log = LoggerFactory.getLogger(ChatLogService.class);
	private static final ChatLogService INSTANCE = new ChatLogService();

	private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ChatLogWriter");
		t.setDaemon(true);
		return t;
	});

	private ChatLogService() {
	}

	public static ChatLogService getInstance() {
		return INSTANCE;
	}

	public void logMessage(Message msg) {
		if (!Config.LOGDB_ENABLED)
			return;

		writer.submit(() -> {
			try {
				Channel ch = msg.getChannel();
				int playerId = msg.getSender().getClientId();
				String channelType = (ch != null && ch.getChannelType() != null) ? ch.getChannelType().name() : "UNKNOWN";
				int channelId = (ch != null) ? ch.getChannelId() : 0;
				String text = new String(msg.getText(), StandardCharsets.UTF_16LE).replace("\u0000", "").trim();
				String name = decodeName(msg.getSender().getIdentifier());

				try (Connection con = ChatLogDatabaseFactory.getConnection();
						PreparedStatement ps = con.prepareStatement(
								"INSERT INTO chat_log (player_id, player_name, channel_type, channel_id, message) VALUES (?, ?, ?, ?, ?)")) {
					ps.setInt(1, playerId);
					ps.setString(2, name);
					ps.setString(3, channelType);
					ps.setInt(4, channelId);
					ps.setString(5, text);
					ps.executeUpdate();
				}
			} catch (Exception e) {
				log.warn("Failed to write chat log entry", e);
			}
		});
	}

	private static String decodeName(byte[] identifier) {
		if (identifier == null || identifier.length == 0)
			return "";
		return new String(identifier, StandardCharsets.UTF_16LE).replace("\u0000", "").trim();
	}

	public void shutdown() {
		writer.shutdown();
	}
}
