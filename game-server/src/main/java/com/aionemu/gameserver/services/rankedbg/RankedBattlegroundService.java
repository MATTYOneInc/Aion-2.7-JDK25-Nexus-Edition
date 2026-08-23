package com.aionemu.gameserver.services.rankedbg;

import java.util.List;
import java.util.Map;

import javolution.util.FastMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import javolution.util.FastMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.gameserver.configs.main.RankedBgConfig;
import com.aionemu.gameserver.dao.RankedBgRatingDAO;
import com.aionemu.gameserver.instance.InstanceEngine;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.rankedbg.RankedBgRating;
import com.aionemu.gameserver.model.team2.group.PlayerGroup;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.WorldMap;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.WorldMapInstanceFactory;

/**
 * Coordinates the ranked battleground system: queues, matchmaking, instance creation
 * and rating persistence.
 *
 * @author Nexus
 */
public class RankedBattlegroundService {

	private static final Logger log = LoggerFactory.getLogger(RankedBattlegroundService.class);
	private static final RankedBattlegroundService instance = new RankedBattlegroundService();

	private final Map<Integer, RankedBgMatch> matches = new FastMap<Integer, RankedBgMatch>().shared();
	private final Map<Integer, RankedBgQueue> queues = new FastMap<Integer, RankedBgQueue>().shared();
	private final Map<Integer, Integer> playerFormat = new FastMap<Integer, Integer>().shared();

	private RankedBattlegroundService() {
	}

	public static RankedBattlegroundService getInstance() {
		return instance;
	}

	public void start() {
		if (!RankedBgConfig.RANKED_BG_ENABLE) {
			return;
		}
		log.info("[RankedBg] Ranked battleground system enabled (map " + RankedBgConfig.RANKED_BG_MAP_ID + ").");
		ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				matchmake();
			}
		}, RankedBgConfig.TICK_SECONDS * 1000, RankedBgConfig.TICK_SECONDS * 1000);
	}

	public boolean isEnabled() {
		return RankedBgConfig.RANKED_BG_ENABLE;
	}

	public enum RegisterResult {
		OK, DISABLED, BAD_FORMAT, WRONG_GROUP_SIZE, NOT_LEADER, ALREADY_QUEUED
	}

	/**
	 * Queue a player (and, for formats > 1, their whole party) for a ranked battleground.
	 * <ul>
	 *   <li>Format 1 requires a solo player (no group).</li>
	 *   <li>Format N (2..6) requires the player to be the party leader of a party of exactly N.</li>
	 * </ul>
	 */
	public RegisterResult register(Player player, int format) {
		if (!RankedBgConfig.RANKED_BG_ENABLE) {
			return RegisterResult.DISABLED;
		}
		if (format < 1 || format > RankedBgConfig.MAX_TEAM_SIZE) {
			return RegisterResult.BAD_FORMAT;
		}
		PlayerGroup group = player.getPlayerGroup2();
		boolean inGroup = group != null;
		int requiredFormat = inGroup ? group.size() : 1;
		if (format != requiredFormat) {
			return RegisterResult.WRONG_GROUP_SIZE;
		}
		if (inGroup && group.getLeader().getObject().getObjectId() != player.getObjectId()) {
			return RegisterResult.NOT_LEADER;
		}

		List<Player> unit = new ArrayList<Player>();
		if (inGroup) {
			for (Player member : group.getMembers()) {
				unit.add(member);
			}
		}
		else {
			unit.add(player);
		}

		// Clear any stale queue entries for members before re-queuing.
		for (Player p : unit) {
			if (playerFormat.containsKey(p.getObjectId())) {
				unregister(p);
			}
		}

		RankedBgQueue queue = getQueue(format);
		if (queue.add(unit)) {
			for (Player p : unit) {
				playerFormat.put(p.getObjectId(), format);
			}
			return RegisterResult.OK;
		}
		return RegisterResult.ALREADY_QUEUED;
	}

	public void unregister(Player player) {
		Integer fmt = playerFormat.remove(player.getObjectId());
		if (fmt == null) {
			return;
		}
		RankedBgQueue queue = queues.get(fmt);
		if (queue != null) {
			List<Player> removed = queue.removeUnitContaining(player);
			if (removed != null) {
				for (Player p : removed) {
					playerFormat.remove(p.getObjectId());
				}
			}
		}
	}

	private RankedBgQueue getQueue(int format) {
		RankedBgQueue queue = queues.get(format);
		if (queue == null) {
			queue = new RankedBgQueue(format);
			queues.put(format, queue);
		}
		return queue;
	}

	private void matchmake() {
		if (!RankedBgConfig.RANKED_BG_ENABLE) {
			return;
		}
		for (RankedBgQueue queue : queues.values()) {
			RankedBgMatch match = queue.tryBuild(RankedBgConfig.RANKED_BG_MAP_ID);
			while (match != null) {
				startMatch(match);
				match = queue.tryBuild(RankedBgConfig.RANKED_BG_MAP_ID);
			}
		}
	}

	private void startMatch(RankedBgMatch match) {
		WorldMap map = World.getInstance().getWorldMap(RankedBgConfig.RANKED_BG_MAP_ID);
		int nextInstanceId = map.getNextInstanceId();
		WorldMapInstance instance = WorldMapInstanceFactory.createWorldMapInstance(map, nextInstanceId);
		InstanceService.startInstanceChecker(instance);
		map.addInstance(nextInstanceId, instance);
		SpawnEngine.spawnInstance(RankedBgConfig.RANKED_BG_MAP_ID, nextInstanceId);

		RankedBgMatch realMatch = new RankedBgMatch(nextInstanceId, RankedBgConfig.RANKED_BG_MAP_ID, match.getFormat(),
			match.getTeamA(), match.getTeamB());
		matches.put(nextInstanceId, realMatch);

		removeFromQueues(realMatch);

		InstanceEngine.getInstance().onInstanceCreate(instance);
		teleportTeams(instance, realMatch);

		log.info("[RankedBg] Started " + realMatch.getFormat() + "v" + realMatch.getFormat() + " match (instance "
			+ nextInstanceId + ").");
	}

	private void removeFromQueues(RankedBgMatch match) {
		for (Player p : match.getTeamA()) {
			playerFormat.remove(p.getObjectId());
		}
		for (Player p : match.getTeamB()) {
			playerFormat.remove(p.getObjectId());
		}
	}

	private void teleportTeams(WorldMapInstance instance, RankedBgMatch match) {
		teleport(match.getTeamA(), RankedBgMatch.TEAM_ELYOS, instance, match);
		teleport(match.getTeamB(), RankedBgMatch.TEAM_ASMODIANS, instance, match);
	}

	private void teleport(List<Player> team, int teamId, WorldMapInstance instance, RankedBgMatch match) {
		float[] base = teamId == RankedBgMatch.TEAM_ELYOS ? match.getTeamASpawn() : match.getTeamBSpawn();
		int i = 0;
		for (Player player : team) {
			float dx = base[0] + (i % 3) * 3.0f;
			float dz = base[2] + (i / 3) * 3.0f;
			match.rememberOrigin(player);
			InstanceService.registerPlayerWithInstance(instance, player);
			com.aionemu.gameserver.services.teleport.TeleportService.teleportTo(player, RankedBgConfig.RANKED_BG_MAP_ID,
				instance.getInstanceId(), dx, base[1], dz, 3000, true);
			i++;
		}
	}

	public RankedBgMatch getMatch(int instanceId) {
		return matches.get(instanceId);
	}

	public void removeMatch(int instanceId) {
		matches.remove(instanceId);
	}

	/* Rating persistence helpers */

	public int loadRating(int playerId, int format) {
		RankedBgRating rating = DAOManager.getDAO(RankedBgRatingDAO.class).load(playerId, format);
		return rating == null ? RankedBgConfig.DEFAULT_RATING : rating.getRating();
	}

	public void saveRating(RankedBgRating rating) {
		DAOManager.getDAO(RankedBgRatingDAO.class).store(rating);
	}

	public RankedBgRating loadOrCreate(int playerId, int format) {
		RankedBgRating rating = DAOManager.getDAO(RankedBgRatingDAO.class).load(playerId, format);
		if (rating == null) {
			rating = new RankedBgRating(playerId, format);
			rating.setRating(RankedBgConfig.DEFAULT_RATING);
		}
		return rating;
	}
}
