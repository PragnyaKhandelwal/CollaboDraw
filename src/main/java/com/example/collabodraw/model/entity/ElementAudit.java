package com.example.collabodraw.model.entity;

import java.time.LocalDateTime;

/**
 * ElementAudit entity representing element change history
 */
public class ElementAudit {
    private Long auditId;
    private Long elementId;
    private Long boardId;
    private String action;
    private Long userId;
    private LocalDateTime actionTime;
    private String beforeData; // JSON string
    private String afterData;  // JSON string

    // Constructors
    public ElementAudit() {}

    public ElementAudit(Long elementId, Long boardId, String action, Long userId, String beforeData, String afterData) {
        this.elementId = elementId;
        this.boardId = boardId;
        this.action = action;
        this.userId = userId;
        this.beforeData = beforeData;
        this.afterData = afterData;
    }

    // Getters and Setters
    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }

    public Long getElementId() { return elementId; }
    public void setElementId(Long elementId) { this.elementId = elementId; }

    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDateTime getActionTime() { return actionTime; }
    public void setActionTime(LocalDateTime actionTime) { this.actionTime = actionTime; }

    public String getBeforeData() { return beforeData; }
    public void setBeforeData(String beforeData) { this.beforeData = beforeData; }

    public String getAfterData() { return afterData; }
    public void setAfterData(String afterData) { this.afterData = afterData; }
}