package com.example.collabodraw.model.entity;

import java.time.LocalDateTime;

/**
 * Session entity representing user sessions
 */
public class Session {
    private Long sessionId;
    private Long boardId;
    private Long userId;
    private LocalDateTime connectedAt;
    private LocalDateTime disconnectedAt;

    // Constructors
    public Session() {}

    public Session(Long boardId, Long userId) {
        this.boardId = boardId;
        this.userId = userId;
    }

    // Getters and Setters
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDateTime getConnectedAt() { return connectedAt; }
    public void setConnectedAt(LocalDateTime connectedAt) { this.connectedAt = connectedAt; }

    public LocalDateTime getDisconnectedAt() { return disconnectedAt; }
    public void setDisconnectedAt(LocalDateTime disconnectedAt) { this.disconnectedAt = disconnectedAt; }
}