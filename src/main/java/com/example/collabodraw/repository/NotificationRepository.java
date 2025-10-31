package com.example.collabodraw.repository;

import com.example.collabodraw.model.entity.Notification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Notification> mapper = new RowMapper<Notification>() {
        @Override
        public Notification mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            Notification n = new Notification();
            n.setId(rs.getLong("notification_id"));
            n.setUserId(rs.getLong("user_id"));
            n.setType(rs.getString("type"));
            n.setTitle(rs.getString("title"));
            n.setMessage(rs.getString("message"));
            n.setRead(rs.getBoolean("is_read"));
            n.setLinkUrl(rs.getString("link_url"));
            Object board = rs.getObject("board_id");
            n.setBoardId(board == null ? null : rs.getLong("board_id"));
            java.sql.Timestamp ts = rs.getTimestamp("created_at");
            n.setCreatedAt(ts == null ? null : ts.toLocalDateTime());
            return n;
        }
    };

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Notification> findRecentByUser(Long userId, int limit) {
        try {
            return jdbcTemplate.query(
                "SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC LIMIT ?",
                mapper,
                userId, limit
            );
        } catch (org.springframework.dao.DataAccessException e) {
            return java.util.Collections.emptyList();
        }
    }

    public void markRead(Long id, Long userId) {
        try {
            jdbcTemplate.update("UPDATE notifications SET is_read=true WHERE notification_id=? AND user_id=?", id, userId);
        } catch (org.springframework.dao.DataAccessException ignored) {}
    }

    public void create(Long userId, String type, String title, String message, String linkUrl, Long boardId, LocalDateTime createdAt) {
        try {
            jdbcTemplate.update(
                "INSERT INTO notifications (user_id, type, title, message, link_url, board_id, is_read, created_at) VALUES (?,?,?,?,?,?,false,?)",
                userId, type, title, message, linkUrl, boardId, java.sql.Timestamp.valueOf(createdAt)
            );
        } catch (org.springframework.dao.DataAccessException ignored) {}
    }
}
