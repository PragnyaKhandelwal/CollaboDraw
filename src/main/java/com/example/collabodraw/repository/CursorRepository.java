package com.example.collabodraw.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class CursorRepository {
    private final JdbcTemplate jdbc;

    public CursorRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Long findCursorId(Long boardId, Long userId) {
        try {
            return jdbc.queryForObject("SELECT cursor_id FROM cursors WHERE board_id=? AND user_id=? ORDER BY updated_at DESC LIMIT 1",
                    Long.class, boardId, userId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void updateCursor(Long cursorId, int x, int y) {
        jdbc.update("UPDATE cursors SET x=?, y=?, updated_at=NOW() WHERE cursor_id=?", x, y, cursorId);
    }

    public Long insertCursor(Long boardId, Long userId, int x, int y) {
        jdbc.update("INSERT INTO cursors (board_id, user_id, x, y) VALUES (?,?,?,?)", boardId, userId, x, y);
        return findCursorId(boardId, userId);
    }

    public List<Map<String, Object>> getBoardCursors(Long boardId) {
        return jdbc.queryForList("SELECT c.cursor_id, u.user_id, u.username, c.x, c.y, c.updated_at FROM cursors c JOIN users u ON c.user_id=u.user_id WHERE c.board_id=? ORDER BY c.updated_at DESC",
                boardId);
    }
}
