package com.example.collabodraw.repository;

import com.example.collabodraw.model.entity.BoardMembership;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Repository for BoardMembership entity operations using JDBC
 * Maps to 'board_membership' table in collaborative_workspace_db
 */
@Repository
public class BoardMembershipRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final BoardMembershipRowMapper membershipRowMapper = new BoardMembershipRowMapper();

    public BoardMembershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int save(BoardMembership membership) {
        String sql = "INSERT INTO board_membership (board_id, user_id, role) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE role = VALUES(role)";
        return jdbcTemplate.update(sql, 
            membership.getBoardId(), 
            membership.getUserId(), 
            membership.getRole());
    }

    public BoardMembership findByBoardIdAndUserId(Long boardId, Long userId) {
        String sql = "SELECT * FROM board_membership WHERE board_id = ? AND user_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, membershipRowMapper, boardId, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<BoardMembership> findByBoardId(Long boardId) {
        String sql = "SELECT * FROM board_membership WHERE board_id = ? ORDER BY joined_at";
        return jdbcTemplate.query(sql, membershipRowMapper, boardId);
    }

    public List<Long> findUserIdsByBoardId(Long boardId) {
        return findByBoardId(boardId).stream()
                .map(BoardMembership::getUserId)
                .collect(Collectors.toList());
    }

    public List<BoardMembership> findByUserId(Long userId) {
        String sql = "SELECT * FROM board_membership WHERE user_id = ? ORDER BY joined_at DESC";
        return jdbcTemplate.query(sql, membershipRowMapper, userId);
    }

    public void delete(Long boardId, Long userId) {
        String sql = "DELETE FROM board_membership WHERE board_id = ? AND user_id = ?";
        jdbcTemplate.update(sql, boardId, userId);
    }

    public void deleteByBoardId(Long boardId) {
        String sql = "DELETE FROM board_membership WHERE board_id = ?";
        jdbcTemplate.update(sql, boardId);
    }

    public boolean hasAccess(Long boardId, Long userId) {
        String sql = "SELECT COUNT(*) FROM board_membership WHERE board_id = ? AND user_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, boardId, userId);
        return count != null && count > 0;
    }

    public boolean isOwner(Long boardId, Long userId) {
        String sql = "SELECT COUNT(*) FROM board_membership WHERE board_id = ? AND user_id = ? AND role = 'owner'";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, boardId, userId);
        return count != null && count > 0;
    }

    /**
     * Toggles favorite/archived on a user's own membership row and returns the new value.
     * Returns null if the caller has no membership row for that board (e.g. not a member).
     */
    public Boolean toggleFavorite(Long boardId, Long userId) {
        return toggleFlag(boardId, userId, "is_favorite");
    }

    public Boolean toggleArchived(Long boardId, Long userId) {
        return toggleFlag(boardId, userId, "is_archived");
    }

    private Boolean toggleFlag(Long boardId, Long userId, String column) {
        // Column name is a fixed internal constant (never user input), so string-building the
        // SQL here is safe - it's not concatenating any request-supplied value.
        Boolean current = jdbcTemplate.query(
                "SELECT " + column + " FROM board_membership WHERE board_id = ? AND user_id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : null,
                boardId, userId);
        if (current == null) return null;
        boolean next = !current;
        jdbcTemplate.update("UPDATE board_membership SET " + column + " = ? WHERE board_id = ? AND user_id = ?",
                next, boardId, userId);
        return next;
    }

    private static class BoardMembershipRowMapper implements RowMapper<BoardMembership> {
        @Override
        public BoardMembership mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            BoardMembership membership = new BoardMembership();
            membership.setBoardId(rs.getLong("board_id"));
            membership.setUserId(rs.getLong("user_id"));
            membership.setRole(rs.getString("role"));
            membership.setFavorite(rs.getBoolean("is_favorite"));
            membership.setArchived(rs.getBoolean("is_archived"));

            // Handle timestamp
            java.sql.Timestamp joinedTimestamp = rs.getTimestamp("joined_at");
            if (joinedTimestamp != null) {
                membership.setJoinedAt(joinedTimestamp.toLocalDateTime());
            }

            return membership;
        }
    }
}