package com.aionemu.gameserver.services.rankedbg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Queue for the "class duel" mode. Every waiting player is a solo entrant; matches are
 * formed only between two players that share the same {@link PlayerClass}. One deque is kept
 * per class so that players of different classes never fight each other.
 *
 * @author Nexus
 */
public class RankedBgClassQueue {

	private final Map<PlayerClass, Deque<Player>> byClass = new HashMap<PlayerClass, Deque<Player>>();

	/** Add a solo player to their class deque. Returns false if already queued. */
	public boolean add(Player player) {
		if (player == null) {
			return false;
		}
		if (contains(player)) {
			return false;
		}
		PlayerClass pc = player.getPlayerClass();
		Deque<Player> dq = byClass.get(pc);
		if (dq == null) {
			dq = new ArrayDeque<Player>();
			byClass.put(pc, dq);
		}
		dq.addLast(player);
		return true;
	}

	/** Remove the given player from whatever class deque they are in. Returns the player if found. */
	public Player remove(Player player) {
		Deque<Player> dq = byClass.get(player.getPlayerClass());
		if (dq != null && dq.remove(player)) {
			return player;
		}
		for (Deque<Player> d : byClass.values()) {
			if (d.remove(player)) {
				return player;
			}
		}
		return null;
	}

	public boolean contains(Player player) {
		Deque<Player> dq = byClass.get(player.getPlayerClass());
		if (dq != null && dq.contains(player)) {
			return true;
		}
		for (Deque<Player> d : byClass.values()) {
			if (d.contains(player)) {
				return true;
			}
		}
		return false;
	}

	/** Number of solo players currently queued for a class. */
	public int size(PlayerClass pc) {
		Deque<Player> dq = byClass.get(pc);
		return dq == null ? 0 : dq.size();
	}

	/**
	 * Build a same-class 1v1 match if some class has at least two waiting players.
	 * Returns null otherwise.
	 */
	public RankedBgMatch tryBuild(int mapId) {
		for (Map.Entry<PlayerClass, Deque<Player>> entry : byClass.entrySet()) {
			Deque<Player> dq = entry.getValue();
			if (dq.size() >= 2) {
				Player a = dq.pollFirst();
				Player b = dq.pollFirst();
				List<Player> teamA = new ArrayList<Player>();
				teamA.add(a);
				List<Player> teamB = new ArrayList<Player>();
				teamB.add(b);
				return new RankedBgMatch(-1, mapId, 1, teamA, teamB, true);
			}
		}
		return null;
	}
}
