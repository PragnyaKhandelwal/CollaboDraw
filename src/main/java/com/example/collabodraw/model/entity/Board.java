package com.example.collabodraw.model.entity;

import java.time.LocalDateTime;

/**
 * Board entity representing a whiteboard in the system
 * Maps to 'boards' table in collaborative_workspace_db
 */
public class Board {
    private Long boardId;
    private Long ownerId;
    private String boardName;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private LocalDateTime lastModified;

    // Constructors
    public Board() {}

    public Board(Long ownerId, String boardName, Boolean isPublic) {
        this.ownerId = ownerId;
        this.boardName = boardName;
        this.isPublic = isPublic != null ? isPublic : false;
    }

    // Getters and Setters
    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }

    // For backward compatibility, keep getId/setId methods
    public Long getId() { return boardId; }
    public void setId(Long id) { this.boardId = id; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getBoardName() { return boardName; }
    public void setBoardName(String boardName) { this.boardName = boardName; }
    
    // For backward compatibility with Whiteboard naming
    public String getName() { return boardName; }
    public void setName(String name) { this.boardName = name; }

    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastModified() { return lastModified; }
    public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }
}