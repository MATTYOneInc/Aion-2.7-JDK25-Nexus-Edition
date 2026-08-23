package com.aionemu.gameserver.services.rankedbg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Queue for a single battleground format (team size). A "unit" is either a single
 * solo player (format 1) or an entire party of {@code format} players. Units of the
 * same race wait in a deque; a match is formed by taking one unit from each race.
 *
 * @author Nexus
 */
public class RankedBgQueue {

	private final int format;
	private final Deque<List<Player>> elyos = new ArrayDeque<List<Player>>();
	private final Deque<List<Player>> asmodians = new ArrayDeque<List<Player>>();

	public RankedBgQueue(int format) {
		this.format = format;
	}

	public int getFormat() {
		return format;
	}

	/** Add a unit (solo player or whole party) to the queue. */
	public boolean add(List<Player> unit) {
		if (unit == null || unit.isEmpty()) {
			return false;
		}
		Race race = unit.get(0).getRace();
		if (race == Race.ELYOS) {
			if (!containsAny(unit)) {
				elyos.addLast(unit);
				return true;
			}
		}
		else if (race == Race.ASMODIANS) {
			if (!containsAny(unit)) {
				asmodians.addLast(unit);
				return true;
			}
		}
		return false;
	}

	/** Remove the unit that contains the given player (clears the whole party). */
	public List<Player> removeUnitContaining(Player player) {
		for (List<Player> u : elyos) {
			if (u.contains(player)) {
				elyos.remove(u);
				return u;
			}
		}
		for (List<Player> u : asmodians) {
			if (u.contains(player)) {
				asmodians.remove(u);
				return u;
			}
		}
		return null;
	}

	public boolean contains(Player player) {
		for (List<Player> u : elyos) {
			if (u.contains(player)) {
				return true;
			}
		}
		for (List<Player> u : asmodians) {
			if (u.contains(player)) {
				return true;
			}
		}
		return false;
	}

	private boolean containsAny(List<Player> unit) {
		for (Player p : unit) {
			if (contains(p)) {
				return true;
			}
		}
		return false;
	}

	public int size(Race race) {
		return race == Race.ELYOS ? elyos.size() : asmodians.size();
	}

	/**
	 * Build a match if both sides have at least one waiting unit, otherwise return null.
	 */
	public RankedBgMatch tryBuild(int mapId) {
		List<Player> teamA = elyos.peekFirst();
		List<Player> teamB = asmodians.peekFirst();
		if (teamA == null || teamB == null) {
			return null;
		}
		elyos.pollFirst();
		asmodians.pollFirst();
		return new RankedBgMatch(-1, mapId, format, teamA, teamB);
	}
}
