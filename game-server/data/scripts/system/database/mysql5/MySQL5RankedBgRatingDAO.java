package mysql5;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DB;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.database.ParamReadStH;
import com.aionemu.gameserver.dao.RankedBgRatingDAO;
import com.aionemu.gameserver.model.rankedbg.RankedBgRating;

/**
 * MySQL5 implementation of {@link RankedBgRatingDAO}.
 *
 * @author Nexus
 */
public class MySQL5RankedBgRatingDAO extends RankedBgRatingDAO {

	private static final Logger log = LoggerFactory.getLogger(MySQL5RankedBgRatingDAO.class);

	private static final String SELECT = "SELECT rating, wins, losses, points FROM ranked_bg_rating WHERE player_id = ? AND `format` = ?";
	private static final String INSERT = "INSERT INTO ranked_bg_rating (player_id, `format`, rating, wins, losses, points) VALUES (?, ?, ?, ?, ?, ?)";
	private static final String UPDATE = "UPDATE ranked_bg_rating SET rating = ?, wins = ?, losses = ?, points = ? WHERE player_id = ? AND `format` = ?";
	private static final String TOP = "SELECT player_id, `format`, rating, wins, losses, points FROM ranked_bg_rating WHERE `format` = ? ORDER BY rating DESC LIMIT ?";

	@Override
	public boolean supports(String databaseName, int majorVersion, int minorVersion) {
		return MySQL5DAOUtils.supports(databaseName, majorVersion, minorVersion);
	}

	@Override
	public RankedBgRating load(final int playerId, final int format) {
		final RankedBgRating[] holder = new RankedBgRating[1];
		DB.select(SELECT, new ParamReadStH() {

			@Override
			public void handleRead(ResultSet rs) throws SQLException {
				if (rs.next()) {
					holder[0] = new RankedBgRating(playerId, format, rs.getInt("rating"), rs.getInt("wins"),
						rs.getInt("losses"), rs.getInt("points"));
				}
			}

			@Override
			public void setParams(PreparedStatement ps) throws SQLException {
				ps.setInt(1, playerId);
				ps.setInt(2, format);
			}
		});
		return holder[0];
	}

	@Override
	@SuppressWarnings("unchecked")
	public void store(RankedBgRating rating) {
		RankedBgRating existing = load(rating.getPlayerId(), rating.getFormat());
		Connection con = null;
		try {
			con = DatabaseFactory.getConnection();
			PreparedStatement ps;
			if (existing == null) {
				ps = con.prepareStatement(INSERT);
				ps.setInt(1, rating.getPlayerId());
				ps.setInt(2, rating.getFormat());
				ps.setInt(3, rating.getRating());
				ps.setInt(4, rating.getWins());
				ps.setInt(5, rating.getLosses());
				ps.setInt(6, rating.getPoints());
			}
			else {
				ps = con.prepareStatement(UPDATE);
				ps.setInt(1, rating.getRating());
				ps.setInt(2, rating.getWins());
				ps.setInt(3, rating.getLosses());
				ps.setInt(4, rating.getPoints());
				ps.setInt(5, rating.getPlayerId());
				ps.setInt(6, rating.getFormat());
			}
			ps.execute();
			ps.close();
		}
		catch (SQLException e) {
			log.error("store ranked_bg_rating", e);
		}
		finally {
			DatabaseFactory.close(con);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<RankedBgRating> top(final int format, final int limit) {
		final List<RankedBgRating> result = new ArrayList<RankedBgRating>();
		DB.select(TOP, new ParamReadStH() {

			@Override
			public void handleRead(ResultSet rs) throws SQLException {
				while (rs.next()) {
					result.add(new RankedBgRating(rs.getInt("player_id"), format, rs.getInt("rating"),
						rs.getInt("wins"), rs.getInt("losses"), rs.getInt("points")));
				}
			}

			@Override
			public void setParams(PreparedStatement ps) throws SQLException {
				ps.setInt(1, format);
				ps.setInt(2, limit);
			}
		});
		return result;
	}
}
