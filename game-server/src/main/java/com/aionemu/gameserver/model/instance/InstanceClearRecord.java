package com.aionemu.gameserver.model.instance;

import java.sql.Timestamp;

/**
 * Best clear-time record of a single player for a single instance (dungeon).
 *
 * @author Nexus
 */
public class InstanceClearRecord {

	private final int playerId;
	private final String playerName;
	private final int mapId;
	private final long bestTimeMs;
	private final int clearCount;
	private final Timestamp lastClearTime;

	public InstanceClearRecord(int playerId, String playerName, int mapId, long bestTimeMs, int clearCount, Timestamp lastClearTime) {
		this.playerId = playerId;
		this.playerName = playerName;
		this.mapId = mapId;
		this.bestTimeMs = bestTimeMs;
		this.clearCount = clearCount;
		this.lastClearTime = lastClearTime;
	}

	public int getPlayerId() {
		return playerId;
	}

	public String getPlayerName() {
		return playerName;
	}

	public int getMapId() {
		return mapId;
	}

	public long getBestTimeMs() {
		return bestTimeMs;
	}

	public int getClearCount() {
		return clearCount;
	}

	public Timestamp getLastClearTime() {
		return lastClearTime;
	}
}
