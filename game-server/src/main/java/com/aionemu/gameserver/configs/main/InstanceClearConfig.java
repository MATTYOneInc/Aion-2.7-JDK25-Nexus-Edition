package com.aionemu.gameserver.configs.main;

import com.aionemu.commons.configuration.Property;

/**
 * Configuration for dungeon speedrun clear-time tracking.
 *
 * The mapping of dungeon (world map / instance location) id to the id(s) of the
 * boss NPC whose death ends the run timer is defined in instanceclear.properties
 * as a single string:
 *
 * instanceclear.bosses = mapId:bossId[,bossId...];mapId:bossId[,bossId...]
 *
 * Example:
 * instanceclear.bosses = 300040000:214904;300100000:215054;300190000:215488
 *
 * When several boss ids are listed for one map, the timer is recorded once the
 * last of those bosses dies (the others are allowed to be optional/unspawned).
 *
 * @author Nexus
 */
public class InstanceClearConfig {

	@Property(key = "instanceclear.bosses", defaultValue = "")
	public static String CLEAR_BOSSES;
}
