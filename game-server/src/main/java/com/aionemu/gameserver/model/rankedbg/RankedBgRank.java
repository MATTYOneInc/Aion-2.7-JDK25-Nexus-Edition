package com.aionemu.gameserver.model.rankedbg;

/**
 * Rank tiers derived from a player's Elo rating. The ladder is purely a display
 * layer over the Elo number; Elo remains the source of truth.
 *
 * Tiers (bottom to top): BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, CROWN, ACE, CONQUEROR.
 * Each non-final tier has 5 divisions (V = lowest, I = highest). CONQUEROR is the
 * single top tier.
 *
 * Elo floors per tier: 500 / 800 / 1100 / 1400 / 1700 / 2000 / 2300 / 2600.
 * Each tier spans 300 Elo, split into 5 divisions of 60 Elo each.
 *
 * @author Nexus
 */
public class RankedBgRank {

	public enum Tier {
		BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, CROWN, ACE, CONQUEROR;

		public String getDisplayName() {
			return name().charAt(0) + name().substring(1).toLowerCase();
		}
	}

	private static final int[] TIER_FLOOR = { 500, 800, 1100, 1400, 1700, 2000, 2300, 2600 };
	private static final int DIVISIONS = 5;
	private static final int DIV_STEP = 60;
	private static final int TIER_SPAN = DIVISIONS * DIV_STEP;

	public static final class Rank {
		public final Tier tier;
		public final int division; // 0..4 (V..I); -1 for CONQUEROR
		public final int elo;
		public final int tierFloor;
		public final int tierCeil;
		public final int divisionFloor;
		public final int divisionCeil;

		public Rank(Tier tier, int division, int elo, int tierFloor, int tierCeil, int divisionFloor, int divisionCeil) {
			this.tier = tier;
			this.division = division;
			this.elo = elo;
			this.tierFloor = tierFloor;
			this.tierCeil = tierCeil;
			this.divisionFloor = divisionFloor;
			this.divisionCeil = divisionCeil;
		}

		public boolean isConqueror() {
			return tier == Tier.CONQUEROR;
		}

		/** Roman numeral for the division: 5 = V (lowest), 1 = I (highest). */
		public String getDivisionLabel() {
			if (isConqueror()) {
				return "";
			}
			return toRoman(DIVISIONS - division);
		}

		public String getTierName() {
			return tier.getDisplayName();
		}

		public String getFullName() {
			if (isConqueror()) {
				return "Conqueror";
			}
			return tier.getDisplayName() + " " + getDivisionLabel();
		}

		/** Progress 0..100 within the current division. */
		public int getDivisionProgress() {
			if (isConqueror()) {
				return 100;
			}
			int span = divisionCeil - divisionFloor;
			if (span <= 0) {
				return 100;
			}
			return clamp((elo - divisionFloor) * 100 / span);
		}

		/** Progress 0..100 within the whole tier. */
		public int getTierProgress() {
			if (isConqueror()) {
				return 100;
			}
			int span = tierCeil - tierFloor;
			if (span <= 0) {
				return 100;
			}
			return clamp((elo - tierFloor) * 100 / span);
		}

		private static int clamp(int v) {
			return Math.max(0, Math.min(100, v));
		}

		private static String toRoman(int n) {
			switch (n) {
				case 1:
					return "I";
				case 2:
					return "II";
				case 3:
					return "III";
				case 4:
					return "IV";
				case 5:
					return "V";
				default:
					return String.valueOf(n);
			}
		}
	}

	public static Rank getRank(int elo) {
		int e = elo;
		if (e < TIER_FLOOR[0]) {
			e = TIER_FLOOR[0];
		}
		int tierIdx = 0;
		for (int i = 0; i < TIER_FLOOR.length; i++) {
			if (e >= TIER_FLOOR[i]) {
				tierIdx = i;
			}
			else {
				break;
			}
		}
		Tier tier = Tier.values()[tierIdx];
		int tierFloor = TIER_FLOOR[tierIdx];
		if (tier == Tier.CONQUEROR) {
			return new Rank(tier, -1, elo, tierFloor, Integer.MAX_VALUE, tierFloor, Integer.MAX_VALUE);
		}
		int offset = e - tierFloor;
		int division = offset / DIV_STEP;
		if (division > DIVISIONS - 1) {
			division = DIVISIONS - 1;
		}
		int divisionFloor = tierFloor + division * DIV_STEP;
		return new Rank(tier, division, elo, tierFloor, tierFloor + TIER_SPAN, divisionFloor, divisionFloor + DIV_STEP);
	}

	private RankedBgRank() {
	}
}
