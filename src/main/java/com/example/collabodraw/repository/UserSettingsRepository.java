package com.example.collabodraw.repository;

import com.example.collabodraw.model.entity.UserSettings;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

@Repository
public class UserSettingsRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<UserSettings> mapper = new RowMapper<UserSettings>() {
        @Override
        public UserSettings mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            UserSettings s = new UserSettings();
            s.setUserId(rs.getLong("user_id"));
            s.setDisplayName(rs.getString("display_name"));
            s.setDescription(rs.getString("description"));
            s.setBio(rs.getString("bio"));
            s.setTheme(rs.getString("theme"));
            s.setLanguage(rs.getString("language"));
            s.setTimezone(rs.getString("timezone"));
            s.setEmailNotifications(rs.getBoolean("email_notifications"));
            s.setPushNotifications(rs.getBoolean("push_notifications"));
            s.setBoardUpdates(rs.getBoolean("board_updates"));
            s.setMentions(rs.getBoolean("mentions"));
            s.setMarketingEmails(rs.getBoolean("marketing_emails"));
            s.setTwoFactorEnabled(rs.getBoolean("two_factor_enabled"));
            return s;
        }
    };

    public UserSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserSettings findByUserId(Long userId) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM user_settings WHERE user_id=?", mapper, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void insertDefaults(Long userId, String displayName) {
        jdbcTemplate.update(
            "INSERT INTO user_settings (user_id, display_name, description, bio, theme, language, timezone, email_notifications, push_notifications, board_updates, mentions, marketing_emails, two_factor_enabled) " +
            "VALUES (?,?,?,?, 'system','en','UTC', true, true, true, true, false, false)",
            userId, displayName, "", "");
    }

    public void update(UserSettings s) {
        jdbcTemplate.update(
            "UPDATE user_settings SET display_name=?, description=?, bio=?, theme=?, language=?, timezone=?, email_notifications=?, push_notifications=?, board_updates=?, mentions=?, marketing_emails=?, two_factor_enabled=? WHERE user_id=?",
            s.getDisplayName(), s.getDescription(), s.getBio(), s.getTheme(), s.getLanguage(), s.getTimezone(),
            s.isEmailNotifications(), s.isPushNotifications(), s.isBoardUpdates(), s.isMentions(), s.isMarketingEmails(), s.isTwoFactorEnabled(),
            s.getUserId()
        );
    }
}
