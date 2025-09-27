package com.example.collabodraw.model.entity;

import java.time.LocalDateTime;

/**
 * ActivityLog entity representing activity tracking in the system
 */
public class ActivityLog {
    private Long logId;
    private Long boardId;
    private Long actorId;
    private String action;
    private Long targetId;
    private String details; // JSON string
    private LocalDateTime atTime;

    // Constructors
    public ActivityLog() {}

    public ActivityLog(Long boardId, Long actorId, String action, Long targetId, String details) {
        this.boardId = boardId;
        this.actorId = actorId;
        this.action = action;
        this.targetId = targetId;
        this.details = details;
    }

    // Getters and Setters
    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }

    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }

    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getAtTime() { return atTime; }
    public void setAtTime(LocalDateTime atTime) { this.atTime = atTime; }
}