package com.example.collabodraw.model.entity;

import java.time.LocalDateTime;

/**
 * Cursor entity representing real-time cursor positions
 */
public class Cursor {
    private Long cursorId;
    private Long boardId;
    private Long userId;
    private Double x;
    private Double y;
    private LocalDateTime updatedAt;

    // Constructors
    public Cursor() {}

    public Cursor(Long boardId, Long userId, Double x, Double y) {
        this.boardId = boardId;
        this.userId = userId;
        this.x = x;
        this.y = y;
    }

    // Getters and Setters
    public Long getCursorId() { return cursorId; }
    public void setCursorId(Long cursorId) { this.cursorId = cursorId; }

    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Double getX() { return x; }
    public void setX(Double x) { this.x = x; }

    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}