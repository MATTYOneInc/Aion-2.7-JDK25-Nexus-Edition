package com.aionemu.gameserver.model.rankedbg;

import com.aionemu.gameserver.model.Race;

/**
 * Persistent ranked battleground rating for a single format (team size).
 *
 * @author Nexus
 */
public class RankedBgRating {

	private final int playerId;
	private final int format;
	private int rating;
	private int wins;
	private int losses;
	private int points;

	public RankedBgRating(int playerId, int format) {
		this.playerId = playerId;
		this.format = format;
		this.rating = 1000;
	}

	public RankedBgRating(int playerId, int format, int rating, int wins, int losses, int points) {
		this.playerId = playerId;
		this.format = format;
		this.rating = rating;
		this.wins = wins;
		this.losses = losses;
		this.points = points;
	}

	public int getPlayerId() {
		return playerId;
	}

	public int getFormat() {
		return format;
	}

	public int getRating() {
		return rating;
	}

	public void setRating(int rating) {
		this.rating = rating;
	}

	public int getWins() {
		return wins;
	}

	public void setWins(int wins) {
		this.wins = wins;
	}

	public int getLosses() {
		return losses;
	}

	public void setLosses(int losses) {
		this.losses = losses;
	}

	public int getPoints() {
		return points;
	}

	public void setPoints(int points) {
		this.points = points;
	}

	/** Race is informational only (used by the leaderboard query when needed). */
	public Race getRace() {
		return Race.PC_ALL;
	}
}
