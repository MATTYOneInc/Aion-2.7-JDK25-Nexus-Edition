/*
 * This file is part of aion-lightning <aion-lightning.com>.
 *
 *  aion-lightning is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  aion-lightning is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with aion-lightning.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver.instance.handlers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.InstanceClearConfig;
import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.gameserver.dao.InstanceClearDAO;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Gatherable;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.StageType;
import com.aionemu.gameserver.model.instance.instancereward.InstanceReward;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.services.NpcShoutsService;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.knownlist.Visitor;
import com.aionemu.gameserver.world.zone.ZoneInstance;

/**
 * @author ATracer
 */
public class GeneralInstanceHandler implements InstanceHandler {

	private static final Logger log = LoggerFactory.getLogger(GeneralInstanceHandler.class);

	/**
	 * Map of world map id -> set of NPC ids whose death marks the instance as
	 * cleared (speedrun completion). Loaded from instanceclear.properties
	 * (instanceclear.bosses). See InstanceClearConfig for the file format.
	 */
	private static Map<Integer, Set<Integer>> END_BOSSES;
	private static boolean bossesLoaded = false;

	private static Map<Integer, Set<Integer>> getEndBosses() {
		if (!bossesLoaded) {
			END_BOSSES = parseBosses(InstanceClearConfig.CLEAR_BOSSES);
			bossesLoaded = true;
		}
		return END_BOSSES;
	}

	/**
	 * Parses the "mapId:bossId[,bossId...];..." mapping from the config string.
	 */
	private static Map<Integer, Set<Integer>> parseBosses(String raw) {
		Map<Integer, Set<Integer>> map = new HashMap<Integer, Set<Integer>>();
		if (raw == null || raw.trim().isEmpty()) {
			return map;
		}
		for (String entry : raw.split(";")) {
			entry = entry.trim();
			if (entry.isEmpty()) {
				continue;
			}
			int idx = entry.indexOf(':');
			if (idx <= 0) {
				continue;
			}
			try {
				int mapId = Integer.parseInt(entry.substring(0, idx).trim());
				Set<Integer> ids = new HashSet<Integer>();
				for (String b : entry.substring(idx + 1).split(",")) {
					b = b.trim();
					if (!b.isEmpty()) {
						ids.add(Integer.parseInt(b));
					}
				}
				if (!ids.isEmpty()) {
					map.put(mapId, ids);
				}
			}
			catch (NumberFormatException e) {
				log.warn("Invalid instanceclear.bosses entry: " + entry);
			}
		}
		return map;
	}

	protected final long creationTime;
	protected WorldMapInstance instance;
	protected int instanceId;
	protected Integer mapId;

	/** Timestamp (ms) when the first player entered the instance. */
	private long instanceStartTime;
	/** Whether the clear time was already recorded for this instance. */
	private boolean cleared;

	public GeneralInstanceHandler() {
		creationTime = System.currentTimeMillis();
	}

	@Override
	public void onInstanceCreate(WorldMapInstance instance) {
		this.instance = instance;
		this.instanceId = instance.getInstanceId();
		this.mapId = instance.getMapId();
		this.instanceStartTime = System.currentTimeMillis();
	}

	@Override
	public void onEnterInstance(Player player) {
	}

	@Override
	public void onDie(Npc npc) {
		if (mapId == null) {
			return;
		}
		Set<Integer> bosses = getEndBosses().get(mapId);
		if (bosses != null && bosses.contains(npc.getNpcId())) {
			// Record only once the last configured end-boss for this map has died.
			boolean allDead = true;
			for (int bossId : bosses) {
				if (bossId == npc.getNpcId()) {
					continue;
				}
				Npc other = instance.getNpc(bossId);
				if (other != null && !other.getLifeStats().isAlreadyDead()) {
					allDead = false;
					break;
				}
			}
			if (allDead) {
				recordInstanceClear();
			}
		}
		else if (bosses == null) {
			// Fallback for dungeons without an explicitly configured end boss:
			// consider cleared when the last HERO/LEGENDARY boss in the instance dies.
			NpcRating rating = npc.getObjectTemplate().getRating();
			if (rating == NpcRating.HERO || rating == NpcRating.LEGENDARY) {
				boolean anyBossAlive = false;
				for (Npc n : instance.getNpcs()) {
					NpcRating r = n.getObjectTemplate().getRating();
					if ((r == NpcRating.HERO || r == NpcRating.LEGENDARY) && !n.getLifeStats().isAlreadyDead()) {
						anyBossAlive = true;
						break;
					}
				}
				if (!anyBossAlive) {
					recordInstanceClear();
				}
			}
		}
	}

	/**
	 * Records the best clear time for every player currently inside the instance.
	 * Called automatically when the configured end boss dies.
	 */
	protected final void recordInstanceClear() {
		if (cleared || instanceStartTime == 0) {
			return;
		}
		cleared = true;
		final long clearTime = System.currentTimeMillis() - instanceStartTime;
		final String formatted = formatClearTime(clearTime);
		instance.doOnAllPlayers(new Visitor<Player>() {

			@Override
			public void visit(Player player) {
				PacketSendUtility.sendYellowMessageOnCenter(player, "Инстанс пройден за " + formatted + "!");
			}
		});
		ThreadPoolManager.getInstance().execute(new Runnable() {

			@Override
			public void run() {
				InstanceClearDAO dao = DAOManager.getDAO(InstanceClearDAO.class);
				for (Player player : instance.getPlayersInside()) {
					dao.recordClear(player.getObjectId(), player.getName(), mapId, clearTime);
				}
			}
		});
	}

	/**
	 * Formats milliseconds as HH:MM:SS.
	 */
	protected static String formatClearTime(long millis) {
		long totalSeconds = millis / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}

	@Override
	public void onInstanceDestroy() {
	}

	@Override
	public void onPlayerLogin(Player player) {
	}

	@Override
	public void onPlayerLogOut(Player player) {
	}

	@Override
	public void onLeaveInstance(Player player) {
	}

	@Override
	public void onOpenDoor(int door) {
	}

	@Override
	public void onEnterZone(Player player, ZoneInstance zone) {
	}

	@Override
	public void onLeaveZone(Player player, ZoneInstance zone) {
	}

	@Override
	public void onPlayMovieEnd(Player player, int movieId) {
	}

	@Override
	public boolean onReviveEvent(Player player) {
		return false;
	}

	protected VisibleObject spawn(int npcId, float x, float y, float z, byte heading) {
		SpawnTemplate template = SpawnEngine.addNewSingleTimeSpawn(mapId, npcId, x, y, z, heading);
		return SpawnEngine.spawnObject(template, instanceId);
	}

	protected VisibleObject spawn(int npcId, float x, float y, float z, byte heading, int staticId) {
		SpawnTemplate template = SpawnEngine.addNewSingleTimeSpawn(mapId, npcId, x, y, z, heading);
		template.setStaticId(staticId);
		return SpawnEngine.spawnObject(template, instanceId);
	}

	protected Npc getNpc(int npcId) {
		return instance.getNpc(npcId);
	}

	protected void sendMsg(int msg, int Obj, boolean isShout, int color) {
		sendMsg(msg, Obj, isShout, color, 0);
	}

	protected void sendMsg(int msg, int Obj, boolean isShout, int color, int time) {
		NpcShoutsService.getInstance().sendMsg(instance, msg, Obj, isShout, color, time);
	}

	protected void sendMsg(int msg) {
		sendMsg(msg, 0, false, 25);
	}
	
	protected void sendMsg(final String str) { // to do system message
		instance.doOnAllPlayers(new Visitor<Player>() {

			@Override
			public void visit(Player player) {
				PacketSendUtility.sendYellowMessageOnCenter(player, str);
			}

		});
	}

	@Override
	public void onExitInstance(Player player) {
	}

	@Override
	public void doReward(Player player) {
	}

	@Override
	public boolean onDie(Player player, Creature lastAttacker) {
		return false;
	}

	@Override
	public void onStopTraining(Player player) {
	}

	@Override
	public void onChangeStage(StageType type) {
	}

	@Override
	public StageType getStage() {
		return StageType.DEFAULT;
	}

	@Override
	public void onDropRegistered(Npc npc) {
	}

	@Override
	public void onGather(Player player) {
	}

	@Override
	public void onGather(Player player, Gatherable gatherable) {
	}

	@Override
	public InstanceReward<?> getInstanceReward() {
		return null;
	}

	@Override
	public boolean onPassFlyingRing(Player player, String flyingRing) {
		return false;
	}

	@Override
	public boolean isEnemy(Player effector, Creature effected) {
		return false;
	}

	@Override
	public boolean isEnemyPlayer(Creature effector, Creature effected) {
		return false;
	}

	@Override
	public void handleUseItemFinish(Player player, int npcId) {
	}

	@Override
	public void handleUseItemFinish(Player player, Npc npc) {		
	}

	@Override
	public void onStopInstance() {
	}

	@Override
	public void sendPacket() {
	}

	@Override
	public void sendSystemMsg(Player player, Creature creature, int rewardPoints) {
	}

	public int getDif(int npcId) {
		return 0;
	}

}