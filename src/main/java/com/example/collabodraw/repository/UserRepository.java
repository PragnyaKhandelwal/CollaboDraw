package com.example.collabodraw.repository;

import com.example.collabodraw.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Repository for User entity operations using JDBC
 */
@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final UserRowMapper userRowMapper = new UserRowMapper();

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int save(User user) {
        String sql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";
        try {
            return jdbcTemplate.update(sql, user.getUsername(), user.getEmail(), user.getPasswordHash());
        } catch (DataAccessException e) {
            log.error("Failed to save user '{}': {}", user.getUsername(), e.getMessage());
            throw e;
        }
    }

    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try {
            return singleResult(jdbcTemplate.query(sql, userRowMapper, username));
        } catch (EmptyResultDataAccessException e) {
            return null; // preserve existing contract (null when not found)
        } catch (DataAccessException e) {
            log.error("Database error while finding user by username '{}': {}", username, e.getMessage());
            throw e;
        }
    }

    public User findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try {
            return singleResult(jdbcTemplate.query(sql, userRowMapper, email));
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (DataAccessException e) {
            log.error("Database error while finding user by email '{}': {}", email, e.getMessage());
            throw e;
        }
    }

    public User findById(Long id) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try {
            return singleResult(jdbcTemplate.query(sql, userRowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (DataAccessException e) {
            log.error("Database error while finding user by id {}: {}", id, e.getMessage());
            throw e;
        }
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, username);
        return count != null && count > 0;
    }

    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }
    
    public int update(User user) {
        String sql = "UPDATE users SET username = ?, email = ? WHERE user_id = ?";
        try {
            return jdbcTemplate.update(sql, user.getUsername(), user.getEmail(), user.getUserId());
        } catch (DataAccessException e) {
            log.error("Failed to update user id {}: {}", user.getUserId(), e.getMessage());
            throw e;
        }
    }

    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("user_id"));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            user.setPasswordHash(rs.getString("password_hash"));

            java.sql.Timestamp createdTimestamp = rs.getTimestamp("created_at");
            if (createdTimestamp != null) {
                user.setCreatedAt(createdTimestamp.toLocalDateTime());
            }
            return user;
        }
    }

    private <T> T singleResult(java.util.List<T> results) {
        if (results == null || results.isEmpty()) return null;
        if (results.size() > 1) {
            log.warn("Expected single result but found {} entries", results.size());
        }
        return results.get(0);
    }
}