package com.aionemu.gameserver.services.bot;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.model.templates.factions.NpcFactionTemplate;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.questEngine.model.QuestState;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.restrictions.RestrictionsManager;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.services.drop.DropService;
import com.aionemu.gameserver.configs.main.BotsConfig;
import com.aionemu.gameserver.controllers.movement.MovementMask;
import com.aionemu.gameserver.controllers.movement.PlayerMoveController;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MOVE;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.model.skill.PlayerSkillEntry;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.MathUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.MapRegion;
import com.aionemu.gameserver.world.World;

/**
 * Finite-state-machine driver for a single bot player. Driven externally by a
 * {@link ThreadPoolManager} tick scheduled in {@link BotManager}.
 *
 * <p>
 * Behavior: wander/idle -&gt; find a killable mob -&gt; move into range -&gt; attack -&gt; loot,
 * with periodic attempts to accept/complete quests from nearby quest-giver NPCs. All actions use
 * the same server-side APIs a real client would trigger, so the bot levels up, loots and progresses
 * quests exactly like a human would.
 * </p>
 */
public class PlayerBotAI {

	private static final Logger log = LoggerFactory.getLogger(PlayerBotAI.class);

	private enum BotState {
		IDLE, MOVING, COMBAT, LOOT, QUEST, WANDER
	}

	private final Player player;
	private BotState state = BotState.IDLE;
	private Npc target;
	private final int spawnMap;
	private final float spawnX, spawnY, spawnZ;
	private float wanderX, wanderY, wanderZ;
	private final float wanderRadius;
	private final float wanderStep;

	public PlayerBotAI(Player player) {
		this.player = player;
		this.spawnMap = player.getWorldId();
		this.spawnX = player.getX();
		this.spawnY = player.getY();
		this.spawnZ = player.getZ();
		// each bot roams around its own (distributed) home with a slightly randomized speed,
		// so they don't all move in lockstep or cluster on the same point
		this.wanderRadius = BotsConfig.BOTS_WANDER_RADIUS;
		this.wanderStep = 2.0f + (float) (Math.random() * 1.5f);
	}

	/** Called periodically by BotManager. */
	public void tick() {
		if (player == null || !player.isSpawned())
			return;

		if (player.getLifeStats().isAlreadyDead()) {
			onDeath();
			return;
		}

		switch (state) {
			case IDLE:
				Npc mob = findMobTarget();
				if (mob != null) {
					target = mob;
					state = BotState.MOVING;
				} else {
					Npc questGiver = findQuestGiver();
					if (questGiver != null) {
						target = questGiver;
						state = BotState.QUEST;
					} else {
						startWander();
					}
				}
				break;
			case WANDER:
				Npc mob2 = findMobTarget();
				if (mob2 != null) {
					target = mob2;
					state = BotState.MOVING;
					break;
				}
				Npc questGiver2 = findQuestGiver();
				if (questGiver2 != null) {
					target = questGiver2;
					state = BotState.QUEST;
					break;
				}
				if (MathUtil.getDistance(player, wanderX, wanderY, wanderZ) <= 2f) {
					pickWanderDestination();
				}
				moveToPoint(wanderX, wanderY, wanderZ);
				break;
			case MOVING:
				if (target == null || target.getLifeStats().isAlreadyDead()) {
					state = BotState.IDLE;
					break;
				}
				if (inAttackRange(target)) {
					state = BotState.COMBAT;
				} else {
					moveToPoint(target.getX(), target.getY(), target.getZ());
				}
				break;
			case COMBAT:
				if (target == null || target.getLifeStats().isAlreadyDead()) {
					state = BotState.LOOT;
					break;
				}
				// wait while a previous cast is still in progress
				if (player.isCasting())
					break;
				if (!inAttackRange(target)) {
					state = BotState.MOVING;
					break;
				}
				sendStopMove();
				player.setTarget(target);
				// occasionally cast a real skill so other players see the casting animation
				// (SM_CASTSPELL is broadcast by the skill engine to everyone nearby)
				if (Math.random() < BotsConfig.BOTS_SKILL_CHANCE && tryCastSkill(target))
					break;
				player.getController().attackTarget(target, 0);
				break;
			case LOOT:
				if (target != null && !target.getLifeStats().isAlreadyDead()) {
					state = BotState.COMBAT;
					break;
				}
				sendStopMove();
				loot(target);
				target = null;
				state = BotState.IDLE;
				break;
			case QUEST:
				if (target == null || target.getLifeStats().isAlreadyDead()) {
					state = BotState.IDLE;
					break;
				}
				if (MathUtil.getDistance(player, target) <= 4) {
					sendStopMove();
					tryQuests(target);
					state = BotState.IDLE;
				} else {
					moveToPoint(target.getX(), target.getY(), target.getZ());
				}
				break;
		}
	}

	private boolean inAttackRange(Npc npc) {
		float range = player.getGameStats().getAttackRange().getCurrent() / 1000f;
		return MathUtil.isInAttackRange(player, npc, range);
	}

	/**
	 * Picks a random known, non-passive skill and casts it on the current target through the normal
	 * skill engine. The engine broadcasts {@code SM_CASTSPELL} to nearby players, so the casting
	 * animation becomes visible to real clients. Returns true if a cast was actually started.
	 */
	private boolean tryCastSkill(Npc target) {
		if (!BotsConfig.BOTS_USE_SKILLS || player.getCastingSkill() != null)
			return false;
		PlayerSkillEntry[] skills = player.getSkillList().getAllSkills();
		if (skills == null || skills.length == 0)
			return false;
		for (int attempt = 0; attempt < 4; attempt++) {
			PlayerSkillEntry entry = skills[(int) (Math.random() * skills.length)];
			int skillId = entry.getSkillId();
			if (skillId == 16001) // "Return" - don't yank the bot back to its bind point
				continue;
			SkillTemplate st = DataManager.SKILL_DATA.getSkillTemplate(skillId);
			if (st == null || st.isPassive())
				continue;
			try {
				player.getController().useSkill(skillId, 0, 0, 0, 0, 0);
			} catch (Exception e) {
				continue;
			}
			if (player.isCasting())
				return true;
		}
		return false;
	}

	private void moveToward(Npc npc) {
		moveToPoint(npc.getX(), npc.getY(), npc.getZ());
	}

	/**
	 * Moves the bot toward a world point, advancing the authoritative position and broadcasting a
	 * proper {@link SM_MOVE} (with STARTMOVE + direction vector) so other players see the bot actually
	 * running rather than teleporting. Mirrors what the connection's CM_MOVE handling does for a real client.
	 */
	private void moveToPoint(float tx, float ty, float tz) {
		float x = player.getX(), y = player.getY(), z = player.getZ();
		double dist = MathUtil.getDistance(x, y, z, tx, ty, tz);
		if (dist <= 0.5f) {
			sendStopMove();
			return;
		}
		float step = (float) Math.min(dist, wanderStep);
		float nx = (float) (x + (tx - x) / dist * step);
		float ny = (float) (y + (ty - y) / dist * step);
		float nz = (float) (z + (tz - z) / dist * step);

		// Never step into a coordinate that has no loaded map region: World.updatePosition()
		// would otherwise "rescue" the player to its bind point, clumping every bot onto one spot
		// and making them invisible. Validate both the destination and the intermediate step.
		if (!isRegionValid(tx, ty, tz) || !isRegionValid(nx, ny, nz)) {
			return;
		}

		byte heading = (byte) (MathUtil.calculateAngleFrom(x, y, tx, ty) / 3);

		PlayerMoveController mc = player.getMoveController();
		mc.movementMask = MovementMask.STARTMOVE;
		mc.setNewDirection(tx, ty, tz, heading);
		mc.vectorX = tx - nx;
		mc.vectorY = ty - ny;
		mc.vectorZ = tz - nz;

		World.getInstance().updatePosition(player, nx, ny, nz, heading, true);
		PacketSendUtility.broadcastPacket(player, new SM_MOVE(player));
	}

	private void sendStopMove() {
		PlayerMoveController mc = player.getMoveController();
		mc.movementMask = 0;
		PacketSendUtility.broadcastPacket(player, new SM_MOVE(player));
	}

	private void startWander() {
		pickWanderDestination();
		state = BotState.WANDER;
	}

	private void pickWanderDestination() {
		for (int attempt = 0; attempt < 10; attempt++) {
			double ang = Math.random() * Math.PI * 2;
			float r = (float) (Math.random() * wanderRadius);
			float wx = spawnX + (float) Math.cos(ang) * r;
			float wy = spawnY + (float) Math.sin(ang) * r;
			if (isRegionValid(wx, wy, spawnZ)) {
				wanderX = wx;
				wanderY = wy;
				wanderZ = spawnZ;
				return;
			}
		}
		// no valid spot found nearby - just idle at home
		wanderX = spawnX;
		wanderY = spawnY;
		wanderZ = spawnZ;
	}

	/**
	 * Returns true if the given coordinate falls inside a loaded map region for the bot's map.
	 * Mirrors the exact lookup used by {@link World#updatePosition}, so we only ever attempt to
	 * move to coordinates that the server can actually place the player at.
	 */
	private boolean isRegionValid(float x, float y, float z) {
		MapRegion region = player.getActiveRegion();
		if (region == null)
			return false;
		return region.getParent().getRegion(x, y, z) != null;
	}

	private Npc findMobTarget() {
		Npc best = null;
		double bestDist = Double.MAX_VALUE;
		Collection<VisibleObject> known = player.getKnownList().getKnownObjects().values();
		for (VisibleObject obj : known) {
			if (!(obj instanceof Npc))
				continue;
			Npc npc = (Npc) obj;
			if (npc.getLifeStats().isAlreadyDead())
				continue;
			if (!RestrictionsManager.canAttack(player, npc))
				continue;
			if (Math.abs(npc.getLevel() - player.getLevel()) > 5)
				continue;
			double d = MathUtil.getDistance(player, npc);
			if (d < bestDist) {
				bestDist = d;
				best = npc;
			}
		}
		return best;
	}

	private Npc findQuestGiver() {
		Npc best = null;
		double bestDist = Double.MAX_VALUE;
		Collection<VisibleObject> known = player.getKnownList().getKnownObjects().values();
		for (VisibleObject obj : known) {
			if (!(obj instanceof Npc))
				continue;
			Npc npc = (Npc) obj;
			if (npc.getLifeStats().isAlreadyDead())
				continue;
			NpcFactionTemplate nft = DataManager.NPC_FACTIONS_DATA.getNpcFactionByNpcId(npc.getNpcId());
			if (nft == null)
				continue;
			List<QuestTemplate> quests = DataManager.QUEST_DATA.getQuestsByNpcFaction(nft.getId(), player);
			if (quests == null || quests.isEmpty())
				continue;
			double d = MathUtil.getDistance(player, npc);
			if (d <= 30 && d < bestDist) {
				bestDist = d;
				best = npc;
			}
		}
		return best;
	}

	private void tryQuests(Npc npc) {
		try {
			NpcFactionTemplate nft = DataManager.NPC_FACTIONS_DATA.getNpcFactionByNpcId(npc.getNpcId());
			if (nft == null)
				return;
			List<QuestTemplate> quests = DataManager.QUEST_DATA.getQuestsByNpcFaction(nft.getId(), player);
			if (quests == null)
				return;
			for (QuestTemplate qt : quests) {
				int questId = qt.getId();
				QuestState qs = player.getQuestStateList().getQuestState(questId);
				QuestEnv env = new QuestEnv(npc, player, questId, 0);
				if (qs == null) {
					if (QuestService.checkStartConditions(env)) {
						QuestService.startQuest(env);
					}
				} else if (qs.getStatus() == QuestStatus.REWARD) {
					QuestService.finishQuest(env);
				}
			}
		} catch (Exception e) {
			log.debug("Bot quest interaction failed: " + e.getMessage());
		}
	}

	private void loot(Npc npc) {
		if (npc == null)
			return;
		try {
			DropService.getInstance().requestDropList(player, npc.getObjectId());
			for (int i = 0; i < 12; i++) {
				try {
					DropService.getInstance().requestDropItem(player, npc.getObjectId(), i, true);
				} catch (Exception ignore) {
					// item index may not exist; safe to ignore
				}
			}
			DropService.getInstance().closeDropList(player, npc.getObjectId());
		} catch (Exception e) {
			log.debug("Bot loot failed: " + e.getMessage());
		}
	}

	private void onDeath() {
		try {
			TeleportService.teleportTo(player, spawnMap, spawnX, spawnY, spawnZ, 0);
			player.getLifeStats().setCurrentHpPercent(100);
			player.getLifeStats().setCurrentMpPercent(100);
		} catch (Exception e) {
			log.debug("Bot respawn failed: " + e.getMessage());
		}
		target = null;
		state = BotState.IDLE;
	}
}
