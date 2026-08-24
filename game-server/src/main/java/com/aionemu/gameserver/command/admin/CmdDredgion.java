package com.aionemu.gameserver.command.admin;

import com.aionemu.gameserver.command.BaseCommand;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.instance.DredgionService2;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * GM command to open Dredgion registration immediately (bypassing the cron schedule).
 * Usage: //dredgion
 * Required access level: 5
 */
public class CmdDredgion extends BaseCommand {

	public CmdDredgion() {
		super();
		setSecurity(5);
		setHelp("//dredgion - open Dredgion registration for all eligible players right now");
	}

	public void execute(Player player, String... params) {
		if (DredgionService2.getInstance().isDredgionAvialable()) {
			PacketSendUtility.sendMessage(player, "Dredgion registration is already open.");
			return;
		}
		DredgionService2.getInstance().startDredgionRegistration();
		PacketSendUtility.sendMessage(player, "Dredgion registration opened for all eligible players (level 46-55).");
	}
}
