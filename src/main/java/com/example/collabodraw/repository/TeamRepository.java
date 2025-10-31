package com.example.collabodraw.repository;

import com.example.collabodraw.model.entity.Team;
import com.example.collabodraw.model.entity.TeamMember;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class TeamRepository {
    private final JdbcTemplate jdbcTemplate;

    public TeamRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Team> teamMapper = new RowMapper<Team>() {
        @Override
        public Team mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            Team t = new Team();
            t.setTeamId(rs.getLong("team_id"));
            t.setOwnerId(rs.getLong("owner_id"));
            t.setName(rs.getString("name"));
            t.setDescription(rs.getString("description"));
            java.sql.Timestamp ts = rs.getTimestamp("created_at");
            t.setCreatedAt(ts == null ? null : ts.toLocalDateTime());
            return t;
        }
    };

    private final RowMapper<TeamMember> memberMapper = new RowMapper<TeamMember>() {
        @Override
        public TeamMember mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            TeamMember m = new TeamMember();
            m.setTeamId(rs.getLong("team_id"));
            m.setUserId(rs.getLong("user_id"));
            m.setRole(rs.getString("role"));
            m.setStatus(rs.getString("status"));
            java.sql.Timestamp ts = rs.getTimestamp("joined_at");
            m.setJoinedAt(ts == null ? null : ts.toLocalDateTime());
            m.setFullName(rs.getString("username"));
            m.setEmail(rs.getString("email"));
            String initials = rs.getString("username");
            initials = (initials == null || initials.isBlank()) ? "U" : initials.substring(0, Math.min(2, initials.length())).toUpperCase();
            m.setInitials(initials);
            // Simple deterministic color based on user id
            String[] colors = {"#3b82f6","#20b97c","#8b5cf6","#f59e0b"};
            int idx = (int)(rs.getLong("user_id") % colors.length);
            m.setAvatarColor(colors[idx]);
            return m;
        }
    };

    public Team findOrCreatePersonalTeam(Long ownerId) {
        List<Team> teams = jdbcTemplate.query("SELECT * FROM teams WHERE owner_id=? ORDER BY created_at ASC LIMIT 1", teamMapper, ownerId);
        if (!teams.isEmpty()) return teams.get(0);
        jdbcTemplate.update("INSERT INTO teams(owner_id, name, description) VALUES(?, ?, ?)", ownerId, "Personal Team", "Default team for your account");
        return jdbcTemplate.queryForObject("SELECT * FROM teams WHERE owner_id=? ORDER BY created_at ASC LIMIT 1", teamMapper, ownerId);
    }

    public List<TeamMember> findMembers(Long teamId) {
        String sql = "SELECT tm.team_id, tm.user_id, tm.role, tm.status, tm.joined_at, u.username, u.email " +
                "FROM team_members tm JOIN users u ON tm.user_id=u.user_id WHERE tm.team_id=? ORDER BY u.username";
        try {
            return jdbcTemplate.query(sql, memberMapper, teamId);
        } catch (org.springframework.dao.DataAccessException e) {
            // In dev/H2 or during bootstrap, table may be absent; return empty for graceful UI
            return java.util.Collections.emptyList();
        }
    }

    public void addMember(Long teamId, Long userId, String role) {
        jdbcTemplate.update("INSERT INTO team_members (team_id, user_id, role, status) VALUES (?,?,?, 'active') ON DUPLICATE KEY UPDATE role=VALUES(role), status='active'",
                teamId, userId, role);
    }

    public void removeMember(Long teamId, Long userId) {
        jdbcTemplate.update("DELETE FROM team_members WHERE team_id=? AND user_id=?", teamId, userId);
    }
}
