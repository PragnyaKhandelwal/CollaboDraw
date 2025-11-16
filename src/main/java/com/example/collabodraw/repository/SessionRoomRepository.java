package com.example.collabodraw.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Maps a human-entered session code to a concrete board id.
 * Backed by a small table with a UNIQUE/PRIMARY KEY on session_code to avoid races.
 */
@Repository
public class SessionRoomRepository {
    private final JdbcTemplate jdbc;
    private final AtomicBoolean ensured = new AtomicBoolean(false);

    public SessionRoomRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private void ensureTable() {
        if (ensured.compareAndSet(false, true)) {
            try {
                jdbc.execute("CREATE TABLE IF NOT EXISTS session_rooms (" +
                        "session_code VARCHAR(64) PRIMARY KEY, " +
                        "board_id BIGINT NOT NULL"
                        + ")");
            } catch (Exception ignored) {
                // If user has no DDL permission or table exists externally, proceed without failing.
            }
        }
    }

    public Long findBoardIdByCode(String code) {
        ensureTable();
        try {
            return jdbc.queryForObject("SELECT board_id FROM session_rooms WHERE session_code=?", Long.class, code);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public boolean createMapping(String code, Long boardId) {
        ensureTable();
        try {
            int n = jdbc.update("INSERT INTO session_rooms(session_code, board_id) VALUES (?,?)", code, boardId);
            return n > 0;
        } catch (DuplicateKeyException ex) {
            return false; // already mapped by a concurrent request
        }
    }
}
