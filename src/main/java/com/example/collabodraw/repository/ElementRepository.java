package com.example.collabodraw.repository;

import com.example.collabodraw.model.entity.Element;
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
 * Repository for Element entity operations using JDBC
 * Maps to 'elements' table in collaborative_workspace_db
 */
@Repository
public class ElementRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final ElementRowMapper elementRowMapper = new ElementRowMapper();

    public ElementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(Element element) {
        String sql = "INSERT INTO elements (board_id, creator_id, type, z_order, data) VALUES (?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, element.getBoardId());
            ps.setLong(2, element.getCreatorId());
            ps.setString(3, element.getType());
            ps.setInt(4, element.getZOrder() != null ? element.getZOrder() : 0);
            ps.setString(5, element.getData());
            return ps;
        }, keyHolder);
        
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public Element findById(Long elementId) {
        String sql = "SELECT * FROM elements WHERE element_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, elementRowMapper, elementId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<Element> findByBoardId(Long boardId) {
        String sql = "SELECT * FROM elements WHERE board_id = ? ORDER BY z_order, created_at";
        return jdbcTemplate.query(sql, elementRowMapper, boardId);
    }

    public List<Element> findByCreatorId(Long creatorId) {
        String sql = "SELECT * FROM elements WHERE creator_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, elementRowMapper, creatorId);
    }

    public void update(Element element) {
        String sql = "UPDATE elements SET type = ?, z_order = ?, data = ?, updated_at = CURRENT_TIMESTAMP WHERE element_id = ?";
        jdbcTemplate.update(sql, 
            element.getType(),
            element.getZOrder(),
            element.getData(),
            element.getElementId());
    }

    public void delete(Long elementId) {
        String sql = "DELETE FROM elements WHERE element_id = ?";
        jdbcTemplate.update(sql, elementId);
    }

    public void deleteByBoardId(Long boardId) {
        String sql = "DELETE FROM elements WHERE board_id = ?";
        jdbcTemplate.update(sql, boardId);
    }

    public boolean existsById(Long elementId) {
        String sql = "SELECT COUNT(*) FROM elements WHERE element_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, elementId);
        return count != null && count > 0;
    }

    public int getMaxZOrder(Long boardId) {
        String sql = "SELECT COALESCE(MAX(z_order), 0) FROM elements WHERE board_id = ?";
        Integer maxZOrder = jdbcTemplate.queryForObject(sql, Integer.class, boardId);
        return maxZOrder != null ? maxZOrder : 0;
    }

    public int countByBoardId(Long boardId) {
        String sql = "SELECT COUNT(*) FROM elements WHERE board_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, boardId);
        return count != null ? count : 0;
    }

    // Snapshot helpers for storing full-board state in a single JSON row
    public String findLatestSnapshotData(Long boardId) {
        String sql = "SELECT data FROM elements WHERE board_id = ? AND type = 'snapshot' ORDER BY updated_at DESC, created_at DESC LIMIT 1";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, boardId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void replaceSnapshot(Long boardId, Long userId, String dataJson) {
        // Remove old snapshots and insert a fresh one
        String del = "DELETE FROM elements WHERE board_id = ? AND type = 'snapshot'";
        jdbcTemplate.update(del, boardId);

        String ins = "INSERT INTO elements (board_id, creator_id, type, z_order, data) VALUES (?, ?, 'snapshot', 0, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, boardId);
            ps.setLong(2, userId);
            ps.setString(3, dataJson);
            return ps;
        }, keyHolder);
    }

    private static class ElementRowMapper implements RowMapper<Element> {
        @Override
        public Element mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            Element element = new Element();
            element.setElementId(rs.getLong("element_id"));
            element.setBoardId(rs.getLong("board_id"));
            element.setCreatorId(rs.getLong("creator_id"));
            element.setType(rs.getString("type"));
            element.setZOrder(rs.getInt("z_order"));
            element.setData(rs.getString("data"));
            
            // Handle timestamps
            java.sql.Timestamp createdTimestamp = rs.getTimestamp("created_at");
            if (createdTimestamp != null) {
                element.setCreatedAt(createdTimestamp.toLocalDateTime());
            }
            
            java.sql.Timestamp updatedTimestamp = rs.getTimestamp("updated_at");
            if (updatedTimestamp != null) {
                element.setUpdatedAt(updatedTimestamp.toLocalDateTime());
            }
            
            return element;
        }
    }
}