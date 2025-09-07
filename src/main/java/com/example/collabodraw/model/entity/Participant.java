package com.example.collabodraw.model.entity;

import java.time.LocalDateTime;

/**
 * Participant entity representing a user's participation in a whiteboard
 */
public class Participant {
    private Long id;
    private Long userId;
    private Long whiteboardId;
    private LocalDateTime createdAt;

    // Constructors
    public Participant() {}

    public Participant(Long userId, Long whiteboardId) {
        this.userId = userId;
        this.whiteboardId = whiteboardId;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getWhiteboardId() { return whiteboardId; }
    public void setWhiteboardId(Long whiteboardId) { this.whiteboardId = whiteboardId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
