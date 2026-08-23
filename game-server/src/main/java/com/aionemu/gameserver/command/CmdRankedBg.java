package com.aionemu.gameserver.command;

import com.aionemu.gameserver.configs.main.RankedBgConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.rankedbg.RankedBgRank;
import com.aionemu.gameserver.model.team2.group.PlayerGroup;
import com.aionemu.gameserver.services.rankedbg.RankedBattlegroundService;
import com.aionemu.gameserver.services.rankedbg.RankedBattlegroundService.RegisterResult;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * Player command for the ranked battleground system.
 * Usage:
 *   .rankedbg join [format]   - queue for a ranked battleground. Format defaults to
 *                               your party size (1 when solo). 1v1 requires being solo;
 *                               NvN (2..6) requires you to be the leader of a party of N.
 *   .rankedbg leave           - leave the queue
 *   .rankedbg rating <format> - show your rating for a format
 *   .rankedbg top <format>    - show the top ratings for a format
 *
 * @author Nexus
 */
public class CmdRankedBg extends BaseCommand {

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
			int format;
			if (params.length >= 2) {
				format = ParseInteger(params[1]);
				if (format < 1 || format > RankedBgConfig.MAX_TEAM_SIZE) {
					PacketSendUtility.sendMessage(player,
						"Format must be between 1 and " + RankedBgConfig.MAX_TEAM_SIZE + ".");
					return;
				}
			}
			else {
				// Derive the format from the player's party (1 when solo).
				PlayerGroup group = player.getPlayerGroup2();
				format = group != null ? group.size() : 1;
			}

			RegisterResult res = RankedBattlegroundService.getInstance().register(player, format);
			switch (res) {
				case OK:
					PacketSendUtility.sendMessage(player, "Queued for " + format + "v" + format + " ranked battleground.");
					break;
				case BAD_FORMAT:
					PacketSendUtility.sendMessage(player,
						"Format must be between 1 and " + RankedBgConfig.MAX_TEAM_SIZE + ".");
					break;
				case WRONG_GROUP_SIZE:
					PlayerGroup g = player.getPlayerGroup2();
					if (g == null) {
						PacketSendUtility.sendMessage(player,
							"You are solo and can only queue 1v1. Join a party of N to queue NvN.");
					}
					else {
						PacketSendUtility.sendMessage(player,
							"Your party size (" + g.size() + ") does not match format " + format
								+ ". You can only queue for " + g.size() + "v" + g.size() + ".");
					}
					break;
				case NOT_LEADER:
					PacketSendUtility.sendMessage(player,
						"Only the party leader can queue the party for a battleground.");
					break;
				case ALREADY_QUEUED:
					PacketSendUtility.sendMessage(player,
						"You or your party are already in the queue.");
					break;
				case DISABLED:
				default:
					PacketSendUtility.sendMessage(player, "Ranked battlegrounds are disabled.");
					break;
			}
		}
		else if ("leave".equalsIgnoreCase(action)) {
			RankedBattlegroundService.getInstance().unregister(player);
			PacketSendUtility.sendMessage(player, "Left the ranked battleground queue.");
		}
		else if ("rating".equalsIgnoreCase(action)) {
			int format = params.length >= 2 ? ParseInteger(params[1]) : 1;
			int rating = RankedBattlegroundService.getInstance().loadRating(player.getObjectId(), format);
			RankedBgRank.Rank rank = RankedBgRank.getRank(rating);
			PacketSendUtility.sendMessage(player,
				"Your rank (" + format + "v" + format + "): " + rank.getFullName() + " (rating " + rating + ")");
		}
		else if ("top".equalsIgnoreCase(action)) {
			int format = params.length >= 2 ? ParseInteger(params[1]) : 1;
			java.util.List<com.aionemu.gameserver.model.rankedbg.RankedBgRating> top = com.aionemu.commons.database.dao.DAOManager
				.getDAO(com.aionemu.gameserver.dao.RankedBgRatingDAO.class).top(format, 10);
			PacketSendUtility.sendMessage(player, "Top " + format + "v" + format + " ratings:");
			int i = 1;
			for (com.aionemu.gameserver.model.rankedbg.RankedBgRating r : top) {
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
		PacketSendUtility.sendMessage(player,
			"Ranked Battleground: join [1-6] | leave | rating <1-6> | top <1-6>");
		PacketSendUtility.sendMessage(player,
			"Solo queues 1v1. A party queues NvN (N = party size); only the party leader may queue.");
	}
}
