package com.example.collabodraw.repository;

import com.example.collabodraw.model.dto.Participant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class SessionRepository {
    private final JdbcTemplate jdbc;
    private static final int PRESENCE_IDLE_TIMEOUT_SECONDS = 12;

    public SessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Long create(Long boardId, Long userId) {
        String sql = "INSERT INTO sessions(board_id, user_id, connected_at) VALUES (?,?, NOW())";
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, boardId);
            ps.setLong(2, userId);
            return ps;
        }, kh);
        return kh.getKey() != null ? kh.getKey().longValue() : null;
    }

    public void end(Long sessionId, Long userId) {
        jdbc.update("UPDATE sessions SET disconnected_at = NOW() WHERE session_id=? AND user_id=? AND disconnected_at IS NULL", sessionId, userId);
    }

    public void endBySessionId(Long sessionId) {
        jdbc.update("UPDATE sessions SET disconnected_at = NOW() WHERE session_id=? AND disconnected_at IS NULL", sessionId);
    }

    public void heartbeat(Long sessionId, Long userId) {
        // reuse connected_at as last-seen for simplicity
        jdbc.update("UPDATE sessions SET connected_at = NOW() WHERE session_id=? AND user_id=? AND disconnected_at IS NULL", sessionId, userId);
    }

    public void heartbeatBySessionId(Long sessionId) {
        jdbc.update("UPDATE sessions SET connected_at = NOW() WHERE session_id=? AND disconnected_at IS NULL", sessionId);
    }

    public void heartbeatByBoard(Long boardId, Long userId) {
        jdbc.update("UPDATE sessions SET connected_at = NOW() WHERE board_id=? AND user_id=? AND disconnected_at IS NULL", boardId, userId);
    }

    public boolean hasActive(Long boardId, Long userId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE board_id=? AND user_id=? AND disconnected_at IS NULL", Integer.class, boardId, userId);
        return c != null && c > 0;
    }

    public Long getActiveSessionId(Long boardId, Long userId) {
        try {
            return jdbc.queryForObject(
                "SELECT session_id FROM sessions WHERE board_id=? AND user_id=? AND disconnected_at IS NULL ORDER BY session_id DESC LIMIT 1",
                Long.class,
                boardId, userId
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<Participant> activeParticipants(Long boardId) {
        // Show users seen recently, including number of active websocket sessions per user.
        String sql = "SELECT u.user_id, u.username, COUNT(*) AS conn_count FROM sessions s JOIN users u ON s.user_id=u.user_id " +
                "WHERE s.board_id=? AND s.disconnected_at IS NULL AND TIMESTAMPDIFF(SECOND, s.connected_at, NOW()) <= ? " +
                "GROUP BY u.user_id, u.username ORDER BY u.username";
        return jdbc.query(sql, (rs, i) -> new Participant(rs.getLong(1), rs.getString(2), rs.getInt(3)), boardId, PRESENCE_IDLE_TIMEOUT_SECONDS);
    }

    public int activeConnectionCount(Long boardId) {
        String sql = "SELECT COUNT(*) FROM sessions WHERE board_id=? AND disconnected_at IS NULL AND TIMESTAMPDIFF(SECOND, connected_at, NOW()) <= ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, boardId, PRESENCE_IDLE_TIMEOUT_SECONDS);
        return count != null ? count : 0;
    }

    public void cleanupStaleSessions(Long boardId) {
        jdbc.update(
            "UPDATE sessions SET disconnected_at = NOW() " +
            "WHERE board_id=? AND disconnected_at IS NULL AND TIMESTAMPDIFF(SECOND, connected_at, NOW()) > ?",
            boardId,
            PRESENCE_IDLE_TIMEOUT_SECONDS
        );
    }
}
