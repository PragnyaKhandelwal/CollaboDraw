package com.example.collabodraw.model.entity;

import java.time.LocalDateTime;

/**
 * ElementVersion entity representing element versioning
 */
public class ElementVersion {
    private Long versionId;
    private Long elementId;
    private Long boardId;
    private Long editorId;
    private Integer versionNum;
    private String data; // JSON string
    private LocalDateTime updatedAt;

    // Constructors
    public ElementVersion() {}

    public ElementVersion(Long elementId, Long boardId, Long editorId, Integer versionNum, String data) {
        this.elementId = elementId;
        this.boardId = boardId;
        this.editorId = editorId;
        this.versionNum = versionNum;
        this.data = data;
    }

    // Getters and Setters
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }

    public Long getElementId() { return elementId; }
    public void setElementId(Long elementId) { this.elementId = elementId; }

    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }

    public Long getEditorId() { return editorId; }
    public void setEditorId(Long editorId) { this.editorId = editorId; }

    public Integer getVersionNum() { return versionNum; }
    public void setVersionNum(Integer versionNum) { this.versionNum = versionNum; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}