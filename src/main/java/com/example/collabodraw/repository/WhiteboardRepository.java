package com.example.collabodraw.repository;

import com.example.collabodraw.model.entity.Whiteboard;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Whiteboard entity operations
 */
@Repository
public class WhiteboardRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final WhiteboardRowMapper whiteboardRowMapper = new WhiteboardRowMapper();

    public WhiteboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int save(Whiteboard whiteboard) {
        String sql = "INSERT INTO whiteboards (title, created_by, created_at, last_active) VALUES (?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();
        return jdbcTemplate.update(
            sql,
            whiteboard.getName(),        // maps to title
            whiteboard.getOwnerId(),     // maps to created_by
            Timestamp.valueOf(now),
            Timestamp.valueOf(now)
        );
    }

    public List<Whiteboard> findByOwnerId(Long ownerId) {
        String sql = "SELECT * FROM whiteboards WHERE created_by = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, whiteboardRowMapper, ownerId);
    }

    public Whiteboard findById(Long id) {
        String sql = "SELECT * FROM whiteboards WHERE board_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, whiteboardRowMapper, id);
        } catch (Exception e) {
            return null;
        }
    }

    public List<Whiteboard> findAll() {
        String sql = "SELECT * FROM whiteboards ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, whiteboardRowMapper);
    }

    private static class WhiteboardRowMapper implements RowMapper<Whiteboard> {
        @Override
        public Whiteboard mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            Whiteboard whiteboard = new Whiteboard();
            whiteboard.setId(rs.getLong("board_id"));                // maps board_id → id
            whiteboard.setName(rs.getString("title"));               // maps title → name
            whiteboard.setOwnerId(rs.getLong("created_by"));         // maps created_by → ownerId

            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                whiteboard.setCreatedAt(createdAt.toLocalDateTime());
            }

            Timestamp lastActive = rs.getTimestamp("last_active");
            if (lastActive != null) {
                whiteboard.setUpdatedAt(lastActive.toLocalDateTime()); // maps last_active → updatedAt
            }

            return whiteboard;
        }
    }
}
