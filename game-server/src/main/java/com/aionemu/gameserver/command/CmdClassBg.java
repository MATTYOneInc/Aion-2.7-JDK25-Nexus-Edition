package com.aionemu.gameserver.command;

import com.aionemu.gameserver.configs.main.RankedBgConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.rankedbg.RankedBgRank;
import com.aionemu.gameserver.services.rankedbg.RankedBattlegroundService;
import com.aionemu.gameserver.services.rankedbg.RankedBattlegroundService.RegisterResult;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * Player command for the "class duel" ranked battleground (1v1, same class only).
 * Usage:
 *   .classbg join   - queue for a same-class 1v1 duel (must be solo; you fight a player
 *                     of your own class).
 *   .classbg leave   - leave the class-duel queue
 *   .classbg rating  - show your class-duel rating and rank
 *   .classbg top     - show the top class-duel ratings
 *
 * @author Nexus
 */
public class CmdClassBg extends BaseCommand {

	@Override
	public void execute(Player player, String... params) {
		if (!RankedBattlegroundService.getInstance().isEnabled()) {
			PacketSendUtility.sendMessage(player, "Ranked battlegrounds are disabled.");
			return;
		}
		if (params.length == 0) {
			showHelp(player);
			return;
		}
		String action = params[0];
		if ("join".equalsIgnoreCase(action)) {
			if (player.getPlayerGroup2() != null) {
				PacketSendUtility.sendMessage(player,
					"You must leave your party to queue for a class duel (1v1, same class).");
				return;
			}
			RegisterResult res = RankedBattlegroundService.getInstance().registerClass(player);
			switch (res) {
				case OK:
					PacketSendUtility.sendMessage(player,
						"Queued for a class duel (1v1 vs " + player.getPlayerClass().name() + ").");
					break;
				case WRONG_GROUP_SIZE:
					PacketSendUtility.sendMessage(player,
						"You must be solo to queue for a class duel. Leave your party first.");
					break;
				case ALREADY_QUEUED:
					PacketSendUtility.sendMessage(player, "You are already in the class-duel queue.");
					break;
				case DISABLED:
				default:
					PacketSendUtility.sendMessage(player, "Ranked battlegrounds are disabled.");
					break;
			}
		}
		else if ("leave".equalsIgnoreCase(action)) {
			RankedBattlegroundService.getInstance().unregister(player);
			PacketSendUtility.sendMessage(player, "Left the class-duel queue.");
		}
		else if ("rating".equalsIgnoreCase(action)) {
			int rating = RankedBattlegroundService.getInstance().loadClassRating(player.getObjectId());
			RankedBgRank.Rank rank = RankedBgRank.getRank(rating);
			PacketSendUtility.sendMessage(player,
				"Your class-duel rank: " + rank.getFullName() + " (rating " + rating + ")");
		}
		else if ("top".equalsIgnoreCase(action)) {
			java.util.List<com.aionemu.gameserver.model.rankedbg.RankedBgClassRating> top = com.aionemu.commons.database.dao.DAOManager
				.getDAO(com.aionemu.gameserver.dao.RankedBgClassRatingDAO.class).top(10);
			PacketSendUtility.sendMessage(player, "Top class-duel ratings:");
			int i = 1;
			for (com.aionemu.gameserver.model.rankedbg.RankedBgClassRating r : top) {
				Player p = com.aionemu.gameserver.world.World.getInstance().findPlayer(r.getPlayerId());
				String name = p != null ? p.getName() : "id:" + r.getPlayerId();
				PacketSendUtility.sendMessage(player, i++ + ". " + name + " - rating " + r.getRating() + " (W"
					+ r.getWins() + "/L" + r.getLosses() + ")");
			}
		}
		else {
			showHelp(player);
		}
	}

	protected void showHelp(Player player) {
		PacketSendUtility.sendMessage(player, "Class Duel: join | leave | rating | top");
		PacketSendUtility.sendMessage(player,
			"1v1 ranked duel against a player of your OWN class. Solo only (leave party first).");
	}
}
