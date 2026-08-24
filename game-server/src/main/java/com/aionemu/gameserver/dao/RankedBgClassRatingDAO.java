package com.aionemu.gameserver.dao;

import com.aionemu.commons.database.dao.DAO;
import com.aionemu.gameserver.model.rankedbg.RankedBgClassRating;

/**
 * Data access object for the class-duel ranked battleground ratings.
 *
 * @author Nexus
 */
public abstract class RankedBgClassRatingDAO implements DAO {

	@Override
	public final String getClassName() {
		return RankedBgClassRatingDAO.class.getName();
	}

	/**
	 * Load the class-duel rating for a player, or null if the row does not exist yet.
	 */
	public abstract RankedBgClassRating load(int playerId);

	/**
	 * Insert or update the rating row (upsert).
	 */
	public abstract void store(RankedBgClassRating rating);

	/**
	 * Top class-duel ratings across all classes.
	 */
	public abstract java.util.List<RankedBgClassRating> top(int limit);
}
