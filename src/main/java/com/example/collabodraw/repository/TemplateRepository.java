package com.example.collabodraw.repository;

import com.example.collabodraw.model.entity.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Repository
public class TemplateRepository {

    private static final Logger log = LoggerFactory.getLogger(TemplateRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final TemplateRowMapper rowMapper = new TemplateRowMapper();

    public TemplateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(Template t) {
        String sql = "INSERT INTO templates (template_key, name, description, category, icon, plan, is_new, is_featured, usage_count) " +
                "VALUES (?,?,?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, t.getTemplateKey());
            ps.setString(2, t.getName());
            ps.setString(3, t.getDescription());
            ps.setString(4, t.getCategory());
            ps.setString(5, t.getIcon());
            ps.setString(6, t.getPlan());
            ps.setBoolean(7, t.isNew());
            ps.setBoolean(8, t.isFeatured());
            ps.setInt(9, t.getUsageCount());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public Template findByKey(String key) {
        String sql = "SELECT * FROM templates WHERE template_key = ?";
        try {
            return jdbcTemplate.queryForObject(sql, rowMapper, key);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public int incrementUsage(String key) {
        String sql = "UPDATE templates SET usage_count = usage_count + 1 WHERE template_key = ?";
        return jdbcTemplate.update(sql, key);
    }

    public List<Template> findAll() {
        try {
            return jdbcTemplate.query("SELECT * FROM templates ORDER BY is_featured DESC, usage_count DESC", rowMapper);
        } catch (DataAccessException e) {
            log.warn("Templates query failed (likely table missing). Returning empty list. Cause: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public List<Template> findByCategory(String category) {
        try {
            return jdbcTemplate.query("SELECT * FROM templates WHERE category = ? ORDER BY usage_count DESC", rowMapper, category);
        } catch (DataAccessException e) {
            log.warn("Templates by category query failed (likely table missing). Returning empty list. Cause: {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public int countAll() {
        try {
            Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM templates", Integer.class);
            return c != null ? c : 0;
        } catch (DataAccessException e) {
            log.warn("Templates countAll failed (likely table missing). Returning 0. Cause: {}", e.getMessage());
            return 0;
        }
    }

    public int countByCategory(String category) {
        try {
            Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM templates WHERE category = ?", Integer.class, category);
            return c != null ? c : 0;
        } catch (DataAccessException e) {
            log.warn("Templates countByCategory failed (likely table missing). Returning 0. Cause: {}", e.getMessage());
            return 0;
        }
    }

    private static class TemplateRowMapper implements RowMapper<Template> {
        @Override
        public Template mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            Template t = new Template();
            t.setTemplateId(rs.getLong("template_id"));
            t.setTemplateKey(rs.getString("template_key"));
            t.setName(rs.getString("name"));
            t.setDescription(rs.getString("description"));
            t.setCategory(rs.getString("category"));
            t.setIcon(rs.getString("icon"));
            t.setPlan(rs.getString("plan"));
            t.setNew(rs.getBoolean("is_new"));
            t.setFeatured(rs.getBoolean("is_featured"));
            t.setUsageCount(rs.getInt("usage_count"));
            return t;
        }
    }
}
