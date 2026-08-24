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
import com.aionemu.gameserver.dao.RankedBgClassRatingDAO;
import com.aionemu.gameserver.model.rankedbg.RankedBgClassRating;

/**
 * MySQL5 implementation of {@link RankedBgClassRatingDAO}.
 *
 * @author Nexus
 */
public class MySQL5RankedBgClassRatingDAO extends RankedBgClassRatingDAO {

	private static final Logger log = LoggerFactory.getLogger(MySQL5RankedBgClassRatingDAO.class);

	private static final String SELECT = "SELECT rating, wins, losses, points FROM ranked_bg_class_rating WHERE player_id = ?";
	private static final String INSERT = "INSERT INTO ranked_bg_class_rating (player_id, rating, wins, losses, points) VALUES (?, ?, ?, ?, ?)";
	private static final String UPDATE = "UPDATE ranked_bg_class_rating SET rating = ?, wins = ?, losses = ?, points = ? WHERE player_id = ?";
	private static final String TOP = "SELECT player_id, rating, wins, losses, points FROM ranked_bg_class_rating ORDER BY rating DESC LIMIT ?";

	@Override
	public boolean supports(String databaseName, int majorVersion, int minorVersion) {
		return MySQL5DAOUtils.supports(databaseName, majorVersion, minorVersion);
	}

	@Override
	public RankedBgClassRating load(final int playerId) {
		final RankedBgClassRating[] holder = new RankedBgClassRating[1];
		DB.select(SELECT, new ParamReadStH() {

			@Override
			public void handleRead(ResultSet rs) throws SQLException {
				if (rs.next()) {
					holder[0] = new RankedBgClassRating(playerId, rs.getInt("rating"), rs.getInt("wins"),
						rs.getInt("losses"), rs.getInt("points"));
				}
			}

			@Override
			public void setParams(PreparedStatement ps) throws SQLException {
				ps.setInt(1, playerId);
			}
		});
		return holder[0];
	}

	@Override
	@SuppressWarnings("unchecked")
	public void store(RankedBgClassRating rating) {
		RankedBgClassRating existing = load(rating.getPlayerId());
		Connection con = null;
		try {
			con = DatabaseFactory.getConnection();
			PreparedStatement ps;
			if (existing == null) {
				ps = con.prepareStatement(INSERT);
				ps.setInt(1, rating.getPlayerId());
				ps.setInt(2, rating.getRating());
				ps.setInt(3, rating.getWins());
				ps.setInt(4, rating.getLosses());
				ps.setInt(5, rating.getPoints());
			}
			else {
				ps = con.prepareStatement(UPDATE);
				ps.setInt(1, rating.getRating());
				ps.setInt(2, rating.getWins());
				ps.setInt(3, rating.getLosses());
				ps.setInt(4, rating.getPoints());
				ps.setInt(5, rating.getPlayerId());
			}
			ps.execute();
			ps.close();
		}
		catch (SQLException e) {
			log.error("store ranked_bg_class_rating", e);
		}
		finally {
			DatabaseFactory.close(con);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<RankedBgClassRating> top(final int limit) {
		final List<RankedBgClassRating> result = new ArrayList<RankedBgClassRating>();
		DB.select(TOP, new ParamReadStH() {

			@Override
			public void handleRead(ResultSet rs) throws SQLException {
				while (rs.next()) {
					result.add(new RankedBgClassRating(rs.getInt("player_id"), rs.getInt("rating"), rs.getInt("wins"),
						rs.getInt("losses"), rs.getInt("points")));
				}
			}

			@Override
			public void setParams(PreparedStatement ps) throws SQLException {
				ps.setInt(1, limit);
			}
		});
		return result;
	}
}
