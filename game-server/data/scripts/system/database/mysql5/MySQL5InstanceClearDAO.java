package mysql5;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.InstanceClearDAO;
import com.aionemu.gameserver.model.instance.InstanceClearRecord;

/**
 * MySQL5 implementation of {@link InstanceClearDAO}.
 *
 * @author Nexus
 */
public class MySQL5InstanceClearDAO extends InstanceClearDAO {

	private static final Logger log = LoggerFactory.getLogger(MySQL5InstanceClearDAO.class);

	private static final String TABLE = "instance_clear_records";

	private static final String CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
		+ " player_id INT NOT NULL,"
		+ " map_id INT NOT NULL,"
		+ " player_name VARCHAR(64) NOT NULL,"
		+ " best_time_ms BIGINT NOT NULL,"
		+ " clear_count INT NOT NULL DEFAULT 1,"
		+ " last_clear_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
		+ " PRIMARY KEY (player_id, map_id)"
		+ ")";

	private static final String RECORD = "INSERT INTO " + TABLE
		+ " (player_id, map_id, player_name, best_time_ms, clear_count, last_clear_time) VALUES (?, ?, ?, ?, 1, ?)"
		+ " ON DUPLICATE KEY UPDATE player_name = VALUES(player_name),"
		+ " best_time_ms = LEAST(best_time_ms, VALUES(best_time_ms)),"
		+ " clear_count = clear_count + 1, last_clear_time = VALUES(last_clear_time)";

	private static final String TOP = "SELECT player_id, player_name, map_id, best_time_ms, clear_count, last_clear_time"
		+ " FROM " + TABLE + " WHERE map_id = ? ORDER BY best_time_ms ASC LIMIT ?";

	@Override
	public boolean supports(String databaseName, int majorVersion, int minorVersion) {
		return MySQL5DAOUtils.supports(databaseName, majorVersion, minorVersion);
	}

	private void ensureTable() {
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(CREATE)) {
			ps.execute();
		} catch (SQLException e) {
			log.error("create " + TABLE, e);
		}
	}

	@Override
	public void recordClear(int playerId, String playerName, int mapId, long timeMs) {
		ensureTable();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(RECORD)) {
			ps.setInt(1, playerId);
			ps.setInt(2, mapId);
			ps.setString(3, playerName);
			ps.setLong(4, timeMs);
			ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
			ps.execute();
		} catch (SQLException e) {
			log.error("record instance clear " + mapId + " player " + playerId, e);
		}
	}

	@Override
	public List<InstanceClearRecord> topByMap(int mapId, int limit) {
		ensureTable();
		List<InstanceClearRecord> result = new ArrayList<InstanceClearRecord>();
		try (Connection con = DatabaseFactory.getConnection(); PreparedStatement ps = con.prepareStatement(TOP)) {
			ps.setInt(1, mapId);
			ps.setInt(2, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					result.add(new InstanceClearRecord(rs.getInt("player_id"), rs.getString("player_name"), rs.getInt("map_id"),
						rs.getLong("best_time_ms"), rs.getInt("clear_count"), rs.getTimestamp("last_clear_time")));
				}
			}
		} catch (SQLException e) {
			log.error("top instance clear " + mapId, e);
		}
		return result;
	}
}
