package com.aionemu.gameserver.model.rankedbg;

/**
 * Elo rating math for the ranked battleground system.
 *
 * @author Nexus
 */
public class Elo {

	/**
	 * Expected score of player A against player B.
	 */
	public static double expected(int ratingA, int ratingB) {
		return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
	}

	/**
	 * New rating after a match.
	 *
	 * @param rating      current rating of the player
	 * @param opponentAvg average rating of the opposing team
	 * @param won         true if the player's team won
	 * @param k           Elo K-factor
	 */
	public static int compute(int rating, int opponentAvg, boolean won, int k) {
		double expected = expected(rating, opponentAvg);
		double score = won ? 1.0 : 0.0;
		return (int) Math.round(rating + k * (score - expected));
	}

	private Elo() {
	}
}
