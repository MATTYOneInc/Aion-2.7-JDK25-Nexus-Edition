package instance.rankedbg;

import java.util.List;

import com.aionemu.commons.network.util.ThreadPoolManager;
import com.aionemu.gameserver.controllers.attack.AggroInfo;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.StaticDoor;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.InstanceScoreType;
import com.aionemu.gameserver.model.instance.instancereward.InstanceReward;
import com.aionemu.gameserver.model.instance.instancereward.RankedBgReward;
import com.aionemu.gameserver.model.instance.playerreward.RankedBgPlayerReward;
import com.aionemu.gameserver.network.aion.serverpackets.SM_DIE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INSTANCE_SCORE;
import com.aionemu.gameserver.services.abyss.AbyssPointsService;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.services.rankedbg.RankedBgMatch;
import com.aionemu.gameserver.services.rankedbg.RankedBattlegroundService;
import com.aionemu.gameserver.services.teleport.TeleportService;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.knownlist.Visitor;

/**
 * Instance handler for a ranked battleground match. Bound to the configured ranked-bg map.
 * Two teams (Elyos vs Asmodians) fight; team score is tracked and Elo ratings are updated
 * when the match ends.
 *
 * @author Nexus
 */
@InstanceID(300350000)
public class RankedBgInstance extends GeneralInstanceHandler {

	private RankedBgReward instanceReward;
	private RankedBgMatch match;
	private boolean finished;
	private boolean started;
	private boolean roundResolving;
	private java.util.concurrent.Future<?> scoreTask;

	@Override
	public void onInstanceCreate(WorldMapInstance instance) {
		super.onInstanceCreate(instance);
		match = RankedBattlegroundService.getInstance().getMatch(instanceId);
		if (match == null) {
			return;
		}
		instanceReward = new RankedBgReward(mapId, instanceId, match.getFormat(), instance);
		for (Player p : match.getTeamA()) {
			instanceReward.regPlayerReward(p, RankedBgMatch.TEAM_ELYOS);
		}
		for (Player p : match.getTeamB()) {
			instanceReward.regPlayerReward(p, RankedBgMatch.TEAM_ASMODIANS);
		}
		instanceReward.setInstanceScoreType(InstanceScoreType.PREPARING);
		instanceReward.setInstanceStartTime();
		started = false;
		sendPacket();
		instance.doOnAllPlayers(new Visitor<Player>() {

			@Override
			public void visit(Player p) {
				PacketSendUtility.sendYellowMessageOnCenter(p,
					"Ranked battleground: prepare! Battle begins in "
						+ com.aionemu.gameserver.configs.main.RankedBgConfig.PREP_SECONDS + " seconds.");
			}
		});

		ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				if (finished) {
					return;
				}
				started = true;
				openDoors();
				instanceReward.setInstanceScoreType(InstanceScoreType.START_PROGRESS);
				sendPacket();
				instance.doOnAllPlayers(new Visitor<Player>() {

					@Override
					public void visit(Player p) {
						PacketSendUtility.sendYellowMessageOnCenter(p, "Ranked battleground started! Fight!");
					}
				});
			}
		}, com.aionemu.gameserver.configs.main.RankedBgConfig.PREP_SECONDS * 1000);

		ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				if (!finished) {
					finishMatch();
				}
			}
		}, (com.aionemu.gameserver.configs.main.RankedBgConfig.PREP_SECONDS
			+ com.aionemu.gameserver.configs.main.RankedBgConfig.MATCH_MINUTES * 60) * 1000);

		// Refresh the scoreboard every second so the countdown timer ticks visibly.
		scoreTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new Runnable() {

			@Override
			public void run() {
				if (finished || instanceReward == null) {
					return;
				}
				sendPacket();
			}
		}, 1000, 1000);
	}

	@Override
	public boolean onDie(Player player, Creature lastAttacker) {
		PacketSendUtility.broadcastPacket(player,
			new SM_EMOTION(player, EmotionType.DIE, 0, lastAttacker == null || player.equals(lastAttacker) ? 0
				: lastAttacker.getObjectId()), true);
		PacketSendUtility.sendPacket(player, new SM_DIE(false, false, 0, 8));

		Player killer = resolveKiller(player, lastAttacker);
		if (killer != null && match != null && killer.getObjectId() != player.getObjectId()) {
			int killerTeam = match.getTeamId(killer);
			int victimTeam = match.getTeamId(player);
			if (killerTeam >= 0 && victimTeam >= 0 && killerTeam != victimTeam) {
				RankedBgPlayerReward kr = instanceReward.getPlayerReward(killer);
				RankedBgPlayerReward vr = instanceReward.getPlayerReward(player);
				if (kr != null) {
					kr.addKill();
				}
				if (vr != null) {
					vr.addDeath();
				}
			}
		}
		sendPacket();
		checkElimination();
		return true;
	}

	private Player resolveKiller(Player victim, Creature lastAttacker) {
		if (lastAttacker instanceof Player) {
			return (Player) lastAttacker;
		}
		Player most = victim.getAggroList().getMostPlayerDamage();
		return most;
	}

	private void checkElimination() {
		if (finished || match == null || !started || roundResolving) {
			return;
		}
		int aliveA = countAlive(match.getTeamA());
		int aliveB = countAlive(match.getTeamB());
		if (aliveA == 0 || aliveB == 0) {
			// One team was fully eliminated: the surviving team wins the round (+1 point).
			roundResolving = true;
			int winningTeam = aliveA == 0 ? RankedBgMatch.TEAM_ASMODIANS : RankedBgMatch.TEAM_ELYOS;
			instanceReward.addTeamPoints(winningTeam, 1);
			if (instanceReward.getTeamScore(winningTeam) >= com.aionemu.gameserver.configs.main.RankedBgConfig.WIN_POINTS) {
				finishMatch();
			}
			else {
				startNextRound(winningTeam);
			}
		}
	}

	private void startNextRound(final int winningTeam) {
		ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				if (finished || match == null) {
					return;
				}
				roundResolving = false;
				instance.doOnAllPlayers(new Visitor<Player>() {

					@Override
					public void visit(Player p) {
						if (!p.getLifeStats().isAlreadyDead()) {
							int missingHp = p.getGameStats().getMaxHp().getCurrent() - p.getLifeStats().getCurrentHp();
							if (missingHp > 0) {
								p.getLifeStats().increaseHp(
									com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.TYPE.HP, missingHp);
							}
							int missingMp = p.getGameStats().getMaxMp().getCurrent() - p.getLifeStats().getCurrentMp();
							if (missingMp > 0) {
								p.getLifeStats().increaseMp(
									com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.TYPE.MP, missingMp);
							}
							p.getGameStats().updateStatsAndSpeedVisually();
							float[] base = match.getTeamId(p) == RankedBgMatch.TEAM_ELYOS ? match.getTeamASpawn()
								: match.getTeamBSpawn();
							TeleportService.teleportTo(p, mapId, instanceId, base[0], base[1], base[2], 3000, true);
						}
						// Dead players revive through their own client timer (onReviveEvent),
						// which teleports them back and closes the death window.
						PacketSendUtility.sendYellowMessage(p,
							(winningTeam == RankedBgMatch.TEAM_ELYOS ? "Elyos" : "Asmodians") + " win the round! Score "
								+ instanceReward.getTeamScore(RankedBgMatch.TEAM_ELYOS) + " - "
								+ instanceReward.getTeamScore(RankedBgMatch.TEAM_ASMODIANS));
					}
				});
				sendPacket();
			}
		}, 3000);
	}

	private void openDoors() {
		for (StaticDoor door : instance.getDoors().values()) {
			if (door != null) {
				door.setOpen(true);
			}
		}
	}

	private int countAlive(List<Player> team) {
		int n = 0;
		for (Player p : team) {
			if (p != null && p.isOnline() && !p.getLifeStats().isAlreadyDead()) {
				n++;
			}
		}
		return n;
	}

	@Override
	public boolean onReviveEvent(Player player) {
		// Properly revive and close the death window (SM_EMOTION RESURRECT is what the
		// client needs to dismiss the revive dialog, matching bind/kisk revive flows).
		com.aionemu.gameserver.services.player.PlayerReviveService.revive(player, 100, 100, false);
		player.getGameStats().updateStatsAndSpeedVisually();
		PacketSendUtility.broadcastPacket(player, new SM_EMOTION(player, EmotionType.RESURRECT), true);
		if (!finished && match != null) {
			int teamId = match.getTeamId(player);
			float[] base = teamId == RankedBgMatch.TEAM_ELYOS ? match.getTeamASpawn() : match.getTeamBSpawn();
			TeleportService.teleportTo(player, mapId, instanceId, base[0], base[1], base[2], 3000, true);
		}
		return true;
	}

	@Override
	public void onLeaveInstance(Player player) {
	}

	@Override
	public void onExitInstance(Player player) {
		if (finished) {
			return;
		}
		// player left; if their whole team is gone, end the match
		checkElimination();
	}

	@Override
	public boolean isEnemyPlayer(Creature effector, Creature effected) {
		if (effector instanceof Player && effected instanceof Player && match != null && started) {
			return match.getTeamId((Player) effector) != match.getTeamId((Player) effected);
		}
		return false;
	}

	@Override
	public InstanceReward<?> getInstanceReward() {
		return instanceReward;
	}

	@Override
	public void sendPacket() {
		if (instanceReward != null) {
			instanceReward.sendPacket();
		}
	}

	private void finishMatch() {
		if (finished || match == null || instanceReward == null) {
			return;
		}
		finished = true;
		if (scoreTask != null) {
			scoreTask.cancel(false);
		}
		instanceReward.setInstanceScoreType(InstanceScoreType.END_PROGRESS);

		int scoreA = instanceReward.getTeamScore(RankedBgMatch.TEAM_ELYOS);
		int scoreB = instanceReward.getTeamScore(RankedBgMatch.TEAM_ASMODIANS);
		int winnerTeam = scoreA == scoreB ? -1 : (scoreA > scoreB ? RankedBgMatch.TEAM_ELYOS : RankedBgMatch.TEAM_ASMODIANS);
		instanceReward.setWinnerTeam(winnerTeam);

		int avgA = averageRating(match.getTeamA());
		int avgB = averageRating(match.getTeamB());

		for (com.aionemu.gameserver.model.instance.playerreward.InstancePlayerReward pr : instanceReward.getInstanceRewards()) {
			RankedBgPlayerReward r = (RankedBgPlayerReward) pr;
			Player p = r.getPlayer();
			if (p == null) {
				continue;
			}
			int teamId = r.getTeamId();
			boolean won = teamId == winnerTeam;
			int oppAvg = teamId == RankedBgMatch.TEAM_ELYOS ? avgB : avgA;

			int before = loadRating(p);
			int after = com.aionemu.gameserver.model.rankedbg.Elo.compute(before, oppAvg, won,
				com.aionemu.gameserver.configs.main.RankedBgConfig.ELO_K);
			r.setRatingBefore(before);
			r.setRatingAfter(after);
			r.setRatingDelta(after - before);
			r.setWinner(won);

			if (match.isClassDuel()) {
				com.aionemu.gameserver.model.rankedbg.RankedBgClassRating rating = RankedBattlegroundService.getInstance()
					.loadOrCreateClass(p.getObjectId());
				rating.setRating(after);
				if (won) {
					rating.setWins(rating.getWins() + 1);
					rating.setPoints(rating.getPoints() + com.aionemu.gameserver.configs.main.RankedBgConfig.SEASON_WIN);
				}
				else {
					rating.setLosses(rating.getLosses() + 1);
					rating.setPoints(rating.getPoints() + com.aionemu.gameserver.configs.main.RankedBgConfig.SEASON_LOSE);
				}
				RankedBattlegroundService.getInstance().saveClassRating(rating);
			}
			else {
				com.aionemu.gameserver.model.rankedbg.RankedBgRating rating = RankedBattlegroundService.getInstance()
					.loadOrCreate(p.getObjectId(), match.getFormat());
				rating.setRating(after);
				if (won) {
					rating.setWins(rating.getWins() + 1);
					rating.setPoints(rating.getPoints() + com.aionemu.gameserver.configs.main.RankedBgConfig.SEASON_WIN);
				}
				else {
					rating.setLosses(rating.getLosses() + 1);
					rating.setPoints(rating.getPoints() + com.aionemu.gameserver.configs.main.RankedBgConfig.SEASON_LOSE);
				}
				RankedBattlegroundService.getInstance().saveRating(rating);
			}

			int ap = won ? com.aionemu.gameserver.configs.main.RankedBgConfig.AP_WIN
				: com.aionemu.gameserver.configs.main.RankedBgConfig.AP_LOSE;
			r.setAp(ap);
			if (p.isOnline()) {
				AbyssPointsService.addAp(p, ap);
				com.aionemu.gameserver.model.rankedbg.RankedBgRank.Rank rb = com.aionemu.gameserver.model.rankedbg.RankedBgRank
					.getRank(before);
				com.aionemu.gameserver.model.rankedbg.RankedBgRank.Rank ra = com.aionemu.gameserver.model.rankedbg.RankedBgRank
					.getRank(after);
				PacketSendUtility.sendYellowMessage(p,
					"Ranked match ended. Rating " + before + " -> " + after + " (" + (after - before) + "). Rank: "
						+ rb.getFullName() + " -> " + ra.getFullName() + ". AP +" + ap);
			}
		}

		sendPacket();
		RankedBattlegroundService.getInstance().removeMatch(instanceId);

		ThreadPoolManager.getInstance().schedule(new Runnable() {

			@Override
			public void run() {
				for (Player p : match.getTeamA()) {
					if (p != null && p.isOnline()) {
						exitToBase(p);
					}
				}
				for (Player p : match.getTeamB()) {
					if (p != null && p.isOnline()) {
						exitToBase(p);
					}
				}
			}
		}, 10000);
	}

	private void exitToBase(Player player) {
		if (match == null) {
			return;
		}
		RankedBgMatch.OriginalLoc origin = match.getOrigin(player.getObjectId());
		if (origin != null) {
			TeleportService.teleportTo(player, origin.mapId, origin.instanceId, origin.x, origin.y, origin.z, 3000, true);
		}
	}

	private int averageRating(List<Player> team) {
		int sum = 0;
		int n = 0;
		for (Player p : team) {
			if (p != null) {
				sum += loadRating(p);
				n++;
			}
		}
		return n == 0 ? com.aionemu.gameserver.configs.main.RankedBgConfig.DEFAULT_RATING : sum / n;
	}

	private int loadRating(Player p) {
		if (match != null && match.isClassDuel()) {
			return RankedBattlegroundService.getInstance().loadClassRating(p.getObjectId());
		}
		return RankedBattlegroundService.getInstance().loadRating(p.getObjectId(), match.getFormat());
	}

	@Override
	public void onInstanceDestroy() {
		finished = true;
		if (scoreTask != null) {
			scoreTask.cancel(false);
		}
		if (instanceReward != null) {
			instanceReward.clear();
		}
	}
}
