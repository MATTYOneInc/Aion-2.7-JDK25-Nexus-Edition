package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

/**
 * Configuration for the ranked PvP battleground system (1v1 .. 6v6).
 *
 * @author Nexus
 */
public class RankedBgConfig {

	/**
	 * Enable the ranked battleground system.
	 */
	@Property(key = "gameserver.rankedbg.enable", defaultValue = "true")
	public static boolean RANKED_BG_ENABLE;

	/**
	 * World map id used for all ranked battleground instances (must be registered in world_maps.xml).
	 */
	@Property(key = "gameserver.rankedbg.mapid", defaultValue = "300350000")
	public static int RANKED_BG_MAP_ID;

	/**
	 * Maximum team size (1 = 1v1, 6 = 6v6).
	 */
	@Property(key = "gameserver.rankedbg.max.team.size", defaultValue = "6")
	public static int MAX_TEAM_SIZE;

	/**
	 * Delay (in seconds) between matchmaking ticks.
	 */
	@Property(key = "gameserver.rankedbg.tick.seconds", defaultValue = "5")
	public static int TICK_SECONDS;

	/**
	 * Match duration in minutes.
	 */
	@Property(key = "gameserver.rankedbg.match.minutes", defaultValue = "10")
	public static int MATCH_MINUTES;

	/**
	 * Preparation countdown before the match starts (in seconds).
	 */
	@Property(key = "gameserver.rankedbg.prep.seconds", defaultValue = "60")
	public static int PREP_SECONDS;

	/**
	 * Points awarded to the killer's team per player kill.
	 */
	@Property(key = "gameserver.rankedbg.kill.points", defaultValue = "100")
	public static int KILL_POINTS;

	/**
	 * Team points needed to win the match. A team earns 1 point each time the entire
	 * opposing team is eliminated (a "round" win).
	 */
	@Property(key = "gameserver.rankedbg.win.points", defaultValue = "3")
	public static int WIN_POINTS;

	/**
	 * Maximum rating difference allowed when matching two teams (Elo tolerance).
	 */
	@Property(key = "gameserver.rankedbg.match.rating.tolerance", defaultValue = "300")
	public static int MATCH_RATING_TOLERANCE;

	/**
	 * Elo K-factor.
	 */
	@Property(key = "gameserver.rankedbg.elo.k", defaultValue = "32")
	public static int ELO_K;

	/**
	 * Abyss Points awarded to winners.
	 */
	@Property(key = "gameserver.rankedbg.ap.win", defaultValue = "1500")
	public static int AP_WIN;

	/**
	 * Abyss Points awarded to losers.
	 */
	@Property(key = "gameserver.rankedbg.ap.lose", defaultValue = "300")
	public static int AP_LOSE;

	/**
	 * Season points awarded for a win.
	 */
	@Property(key = "gameserver.rankedbg.season.win", defaultValue = "10")
	public static int SEASON_WIN;

	/**
	 * Season points awarded for a loss.
	 */
	@Property(key = "gameserver.rankedbg.season.lose", defaultValue = "3")
	public static int SEASON_LOSE;

	/**
	 * Starting Elo rating for new players.
	 */
	@Property(key = "gameserver.rankedbg.default.rating", defaultValue = "1000")
	public static int DEFAULT_RATING;
}
