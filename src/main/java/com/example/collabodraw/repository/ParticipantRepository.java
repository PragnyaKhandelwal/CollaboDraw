package com.example.collabodraw.repository;

import com.example.collabodraw.model.entity.Participant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Participant entity operations
 */
@Repository
public class ParticipantRepository {
    
    private final JdbcTemplate jdbcTemplate;

    public ParticipantRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int save(Participant participant) {
        String sql = "INSERT INTO participants (user_id, whiteboard_id, created_at) VALUES (?, ?, ?)";
        return jdbcTemplate.update(sql, participant.getUserId(), participant.getWhiteboardId(), LocalDateTime.now());
    }

    public List<Participant> findParticipantsByWhiteboardId(Long whiteboardId) {
        String sql = "SELECT * FROM participants WHERE whiteboard_id = ?";
        return jdbcTemplate.query(sql, new ParticipantRowMapper(), whiteboardId);
    }

    public List<Participant> findParticipantsByUserId(Long userId) {
        String sql = "SELECT * FROM participants WHERE user_id = ?";
        return jdbcTemplate.query(sql, new ParticipantRowMapper(), userId);
    }

    public List<Long> findUserIdsByWhiteboardId(Long whiteboardId) {
        String sql = "SELECT user_id FROM participants WHERE whiteboard_id = ?";
        return jdbcTemplate.queryForList(sql, Long.class, whiteboardId);
    }

    public List<Long> findWhiteboardIdsByUserId(Long userId) {
        String sql = "SELECT whiteboard_id FROM participants WHERE user_id = ?";
        return jdbcTemplate.queryForList(sql, Long.class, userId);
    }

    public boolean existsByUserIdAndWhiteboardId(Long userId, Long whiteboardId) {
        String sql = "SELECT COUNT(*) FROM participants WHERE user_id = ? AND whiteboard_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, whiteboardId);
        return count != null && count > 0;
    }

    public int removeParticipant(Long userId, Long whiteboardId) {
        String sql = "DELETE FROM participants WHERE user_id = ? AND whiteboard_id = ?";
        return jdbcTemplate.update(sql, userId, whiteboardId);
    }

    private static class ParticipantRowMapper implements RowMapper<Participant> {
        @Override
        public Participant mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            Participant participant = new Participant();
            participant.setId(rs.getLong("id"));
            participant.setUserId(rs.getLong("user_id"));
            participant.setWhiteboardId(rs.getLong("whiteboard_id"));
            participant.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return participant;
        }
    }
}
