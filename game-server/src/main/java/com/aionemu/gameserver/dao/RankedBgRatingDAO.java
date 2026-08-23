package com.aionemu.gameserver.dao;

import com.aionemu.commons.database.dao.DAO;
import com.aionemu.gameserver.model.rankedbg.RankedBgRating;

/**
 * Data access object for ranked battleground ratings.
 *
 * @author Nexus
 */
public abstract class RankedBgRatingDAO implements DAO {

	@Override
	public final String getClassName() {
		return RankedBgRatingDAO.class.getName();
	}

	/**
	 * Load the rating for a player/format, or null if the row does not exist yet.
	 */
	public abstract RankedBgRating load(int playerId, int format);

	/**
	 * Insert or update the rating row (upsert).
	 */
	public abstract void store(RankedBgRating rating);

	/**
	 * Top ratings for a given format.
	 */
	public abstract java.util.List<RankedBgRating> top(int format, int limit);
}
