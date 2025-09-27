package com.example.collabodraw.model.entity;

import java.time.LocalDateTime;

/**
 * Element entity representing drawing elements on boards
 * Maps to 'elements' table in collaborative_workspace_db
 */
public class Element {
    private Long elementId;
    private Long boardId;
    private Long creatorId;
    private String type;
    private Integer zOrder;
    private String data; // JSON data
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructors
    public Element() {}

    public Element(Long boardId, Long creatorId, String type, String data) {
        this.boardId = boardId;
        this.creatorId = creatorId;
        this.type = type;
        this.data = data;
        this.zOrder = 0;
    }

    // Getters and Setters
    public Long getElementId() { return elementId; }
    public void setElementId(Long elementId) { this.elementId = elementId; }

    // For backward compatibility
    public Long getId() { return elementId; }
    public void setId(Long id) { this.elementId = id; }

    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getZOrder() { return zOrder; }
    public void setZOrder(Integer zOrder) { this.zOrder = zOrder; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}