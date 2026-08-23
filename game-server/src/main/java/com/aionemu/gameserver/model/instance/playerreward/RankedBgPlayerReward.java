package com.aionemu.gameserver.model.instance.playerreward;

import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Per-player reward for a ranked battleground match.
 *
 * @author Nexus
 */
public class RankedBgPlayerReward extends PvPArenaPlayerReward {

	private int teamId;
	private int kills;
	private int deaths;
	private boolean winner;
	private int ap;
	private int ratingBefore;
	private int ratingAfter;
	private int ratingDelta;

	public RankedBgPlayerReward(Player player, int teamId) {
		super(player, 0);
		this.teamId = teamId;
	}

	public int getTeamId() {
		return teamId;
	}

	public void setTeamId(int teamId) {
		this.teamId = teamId;
	}

	public int getKills() {
		return kills;
	}

	public void addKill() {
		kills++;
	}

	public int getDeaths() {
		return deaths;
	}

	public void addDeath() {
		deaths++;
	}

	public boolean isWinner() {
		return winner;
	}

	public void setWinner(boolean winner) {
		this.winner = winner;
	}

	public int getAp() {
		return ap;
	}

	public void setAp(int ap) {
		this.ap = ap;
	}

	public int getRatingBefore() {
		return ratingBefore;
	}

	public void setRatingBefore(int ratingBefore) {
		this.ratingBefore = ratingBefore;
	}

	public int getRatingAfter() {
		return ratingAfter;
	}

	public void setRatingAfter(int ratingAfter) {
		this.ratingAfter = ratingAfter;
	}

	public int getRatingDelta() {
		return ratingDelta;
	}

	public void setRatingDelta(int ratingDelta) {
		this.ratingDelta = ratingDelta;
	}

	@Override
	public int getPoints() {
		return kills;
	}

	@Override
	public int getPvPKills() {
		return kills;
	}
}
