package com.aionemu.gameserver.model.instance.instancereward;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.instance.playerreward.PvPArenaPlayerReward;
import com.aionemu.gameserver.model.instance.playerreward.RankedBgPlayerReward;
import com.aionemu.gameserver.model.instance.InstanceScoreType;
import com.aionemu.gameserver.network.aion.serverpackets.SM_INSTANCE_SCORE;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.knownlist.Visitor;

import javolution.util.FastList;

/**
 * Team-based reward holder for a ranked battleground match. Extends PvPArenaReward so the
 * generic SM_INSTANCE_SCORE packet (case 300350000) can render the scoreboard without a cast
 * failure.
 *
 * @author Nexus
 */
public class RankedBgReward extends PvPArenaReward {

	private final int format;
	private final int[] teamScore = new int[2];
	private int winnerTeam = -1;
	private long instanceTime;

	public RankedBgReward(Integer mapId, int instanceId, int format, WorldMapInstance instance) {
		super(mapId, instanceId, instance);
		this.format = format;
		setInstanceScoreType(InstanceScoreType.PREPARING);
	}

	public int getFormat() {
		return format;
	}

	public void regPlayerReward(Player player, int teamId) {
		if (!containPlayer(player)) {
			addPlayerReward(new RankedBgPlayerReward(player, teamId));
		}
	}

	@Override
	public RankedBgPlayerReward getPlayerReward(Player player) {
		return (RankedBgPlayerReward) super.getPlayerReward(player);
	}

	public void addTeamPoints(int teamId, int points) {
		if (teamId == 0 || teamId == 1) {
			teamScore[teamId] += points;
		}
	}

	public int getTeamScore(int teamId) {
		return teamScore[teamId];
	}

	/**
	 * Rank computation without lambdaj/cglib (which fails on JDK 9+ module system).
	 */
	@Override
	public int getRank(int points) {
		int rank = -1;
		for (PvPArenaPlayerReward pr : getInstanceRewards()) {
			if (pr.getScorePoints() >= points) {
				rank++;
			}
		}
		return rank;
	}

	public void setWinnerTeam(int teamId) {
		this.winnerTeam = teamId;
	}

	public int getWinnerTeam() {
		return winnerTeam;
	}

	public boolean isTeamWinner(int teamId) {
		return winnerTeam == teamId;
	}

	public FastList<RankedBgPlayerReward> getPlayersByTeam(int teamId) {
		FastList<RankedBgPlayerReward> list = new FastList<RankedBgPlayerReward>();
		for (PvPArenaPlayerReward pr : getInstanceRewards()) {
			RankedBgPlayerReward r = (RankedBgPlayerReward) pr;
			if (r.getTeamId() == teamId) {
				list.add(r);
			}
		}
		return list;
	}

	public void setInstanceStartTime() {
		this.instanceTime = System.currentTimeMillis();
	}

	public int getTime() {
		if (isRewarded()) {
			return 0;
		}
		long elapsed = System.currentTimeMillis() - instanceTime;
		long total = com.aionemu.gameserver.configs.main.RankedBgConfig.MATCH_MINUTES * 60L * 1000L;
		long remaining = total - elapsed;
		return (int) (remaining > 0 ? remaining : 0);
	}

	public void sendPacket() {
		instance.doOnAllPlayers(new Visitor<Player>() {

			@Override
			public void visit(Player player) {
				PacketSendUtility.sendPacket(player, new SM_INSTANCE_SCORE(getTime(), getInstanceReward()));
			}
		});
	}
}
