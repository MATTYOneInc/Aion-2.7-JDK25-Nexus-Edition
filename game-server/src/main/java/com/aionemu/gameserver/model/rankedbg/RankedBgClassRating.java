package com.aionemu.gameserver.model.rankedbg;

/**
 * Persistent ranked battleground rating for the "class duel" mode (1v1, same class only).
 * Each player has a single class-duel rating (their class is taken from players.player_class),
 * so the row is keyed only by player id. All Elo math, ranks and divisions are reused from
 * the regular ranked battleground system.
 *
 * @author Nexus
 */
public class RankedBgClassRating {

	private final int playerId;
	private int rating;
	private int wins;
	private int losses;
	private int points;

	public RankedBgClassRating(int playerId) {
		this.playerId = playerId;
		this.rating = 1000;
	}

	public RankedBgClassRating(int playerId, int rating, int wins, int losses, int points) {
		this.playerId = playerId;
		this.rating = rating;
		this.wins = wins;
		this.losses = losses;
		this.points = points;
	}

	public int getPlayerId() {
		return playerId;
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
}
