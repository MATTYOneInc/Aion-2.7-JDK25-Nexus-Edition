package com.aionemu.gameserver.dao;

import java.util.List;

import com.aionemu.commons.database.dao.DAO;
import com.aionemu.gameserver.model.instance.InstanceClearRecord;

/**
 * Persists the best dungeon clear times per player.
 *
 * @author Nexus
 */
public abstract class InstanceClearDAO implements DAO {

	@Override
	public final String getClassName() {
		return InstanceClearDAO.class.getName();
	}

	/**
	 * Records a clear for a player. If the player already has a record for this
	 * map, the best (lowest) time is kept and the clear counter incremented.
	 */
	public abstract void recordClear(int playerId, String playerName, int mapId, long timeMs);

	/**
	 * Top fastest clears for a given dungeon map.
	 */
	public abstract List<InstanceClearRecord> topByMap(int mapId, int limit);
}
