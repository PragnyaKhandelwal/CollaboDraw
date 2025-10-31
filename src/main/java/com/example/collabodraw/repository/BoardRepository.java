package com.example.collabodraw.repository;

import com.example.collabodraw.model.entity.Board;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Repository for Board entity operations using JDBC
 * Maps to 'boards' table in collaborative_workspace_db
 */
@Repository
public class BoardRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final BoardRowMapper boardRowMapper = new BoardRowMapper();

    public BoardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(Board board) {
        String sql = "INSERT INTO boards (owner_id, board_name, is_public) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, board.getOwnerId());
            ps.setString(2, board.getBoardName());
            ps.setBoolean(3, board.getIsPublic() != null ? board.getIsPublic() : false);
            return ps;
        }, keyHolder);
        
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public Board findById(Long boardId) {
        String sql = "SELECT * FROM boards WHERE board_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, boardRowMapper, boardId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<Board> findByOwnerId(Long ownerId) {
        String sql = "SELECT * FROM boards WHERE owner_id = ? ORDER BY last_modified DESC";
        return jdbcTemplate.query(sql, boardRowMapper, ownerId);
    }

    public List<Board> findAll() {
        String sql = "SELECT * FROM boards ORDER BY last_modified DESC";
        return jdbcTemplate.query(sql, boardRowMapper);
    }

    public List<Board> findPublicBoards() {
        String sql = "SELECT * FROM boards WHERE is_public = true ORDER BY last_modified DESC";
        return jdbcTemplate.query(sql, boardRowMapper);
    }

    public int countByOwnerInDays(Long ownerId, int days) {
        String sql = "SELECT COUNT(*) FROM boards WHERE owner_id = ? AND created_at >= NOW() - INTERVAL ? DAY";
        Integer c = jdbcTemplate.queryForObject(sql, Integer.class, ownerId, days);
        return c != null ? c : 0;
    }

    public void updateLastModified(Long boardId) {
        String sql = "UPDATE boards SET last_modified = CURRENT_TIMESTAMP WHERE board_id = ?";
        jdbcTemplate.update(sql, boardId);
    }

    public void updateName(Long boardId, String name) {
        String sql = "UPDATE boards SET board_name = ?, last_modified = CURRENT_TIMESTAMP WHERE board_id = ?";
        jdbcTemplate.update(sql, name, boardId);
    }

    public void delete(Long boardId) {
        String sql = "DELETE FROM boards WHERE board_id = ?";
        jdbcTemplate.update(sql, boardId);
    }

    public boolean existsById(Long boardId) {
        String sql = "SELECT COUNT(*) FROM boards WHERE board_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, boardId);
        return count != null && count > 0;
    }

    private static class BoardRowMapper implements RowMapper<Board> {
        @Override
        public Board mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            Board board = new Board();
            board.setBoardId(rs.getLong("board_id"));
            board.setOwnerId(rs.getLong("owner_id"));
            board.setBoardName(rs.getString("board_name"));
            board.setIsPublic(rs.getBoolean("is_public"));
            
            // Handle timestamps
            java.sql.Timestamp createdTimestamp = rs.getTimestamp("created_at");
            if (createdTimestamp != null) {
                board.setCreatedAt(createdTimestamp.toLocalDateTime());
            }
            
            java.sql.Timestamp lastModifiedTimestamp = rs.getTimestamp("last_modified");
            if (lastModifiedTimestamp != null) {
                board.setLastModified(lastModifiedTimestamp.toLocalDateTime());
            }
            
            return board;
        }
    }
}