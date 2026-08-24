package com.aionemu.gameserver.services.rankedbg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Describes one ranked battleground match: two teams (by race) and the format.
 *
 * @author Nexus
 */
public class RankedBgMatch {

	/** Team 0 = Elyos, Team 1 = Asmodians. */
	public static final int TEAM_ELYOS = 0;
	public static final int TEAM_ASMODIANS = 1;

	/**
	 * Per-stage spawn anchors (Team A / Team B) taken from pvp_zone_data.xml for map 300350000.
	 * Each pair sits on the SAME stage/level so both teams spawn on one floor. The match picks
	 * one stage at random.
	 */
	private static final float[][][] STAGES = {
		{ { 1799.69f, 1749.94f, 311.18f }, { 1887.01f, 1716.00f, 311.18f } }, // stage 1 (Elyos platform)
		{ { 632.50f, 1777.52f, 184.80f }, { 721.20f, 1777.84f, 174.65f } }, // stage 2 (Asmo platform)
		{ { 1291.32f, 1089.27f, 339.97f }, { 1381.68f, 1060.22f, 340.51f } }, // stage 3 (center)
		{ { 1877.83f, 1205.91f, 269.95f }, { 1989.94f, 1192.51f, 273.07f } } // stage 4
	};

	private final int instanceId;
	private final int mapId;
	private final int format;
	private final List<Player> teamA;
	private final List<Player> teamB;
	private final boolean classDuel;
	private final Map<Integer, OriginalLoc> origins = new HashMap<Integer, OriginalLoc>();
	private final float[] teamASpawn;
	private final float[] teamBSpawn;

	public RankedBgMatch(int instanceId, int mapId, int format, List<Player> teamA, List<Player> teamB) {
		this(instanceId, mapId, format, teamA, teamB, false);
	}

	public RankedBgMatch(int instanceId, int mapId, int format, List<Player> teamA, List<Player> teamB, boolean classDuel) {
		this.instanceId = instanceId;
		this.mapId = mapId;
		this.format = format;
		this.teamA = teamA;
		this.teamB = teamB;
		this.classDuel = classDuel;
		int stage = Rnd.get(STAGES.length);
		this.teamASpawn = STAGES[stage][0];
		this.teamBSpawn = STAGES[stage][1];
	}

	public float[] getTeamASpawn() {
		return teamASpawn;
	}

	public float[] getTeamBSpawn() {
		return teamBSpawn;
	}

	/**
	 * Remember a player's location before they are pulled into the arena (so they can be returned).
	 */
	public void rememberOrigin(Player player) {
		origins.put(player.getObjectId(),
			new OriginalLoc(player.getWorldId(), player.getInstanceId(), player.getX(), player.getY(), player.getZ()));
	}

	public OriginalLoc getOrigin(int objectId) {
		return origins.get(objectId);
	}

	public int getInstanceId() {
		return instanceId;
	}

	public int getMapId() {
		return mapId;
	}

	public int getFormat() {
		return format;
	}

	/** True when this match is a same-class 1v1 duel (separate rating track). */
	public boolean isClassDuel() {
		return classDuel;
	}

	public List<Player> getTeamA() {
		return teamA;
	}

	public List<Player> getTeamB() {
		return teamB;
	}

	public int getTeamId(Player player) {
		int obj = player.getObjectId();
		for (Player p : teamA) {
			if (p.getObjectId() == obj) {
				return TEAM_ELYOS;
			}
		}
		for (Player p : teamB) {
			if (p.getObjectId() == obj) {
				return TEAM_ASMODIANS;
			}
		}
		return -1;
	}

	public List<Player> getTeam(int teamId) {
		return teamId == TEAM_ELYOS ? teamA : teamB;
	}

	public List<Player> getOpponentTeam(int teamId) {
		return teamId == TEAM_ELYOS ? teamB : teamA;
	}

	/**
	 * Snapshot of a player's world location, used to return them after the match.
	 */
	public static class OriginalLoc {
		public final int mapId;
		public final int instanceId;
		public final float x;
		public final float y;
		public final float z;

		public OriginalLoc(int mapId, int instanceId, float x, float y, float z) {
			this.mapId = mapId;
			this.instanceId = instanceId;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
}
