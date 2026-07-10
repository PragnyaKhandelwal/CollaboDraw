package com.example.collabodraw.model.entity;

import java.time.LocalDateTime;

/**
 * BoardMembership entity representing user participation in boards
 * Maps to 'board_membership' table in collaborative_workspace_db
 */
public class BoardMembership {
    private Long boardId;
    private Long userId;
    private String role; // 'owner', 'editor', 'viewer'
    private LocalDateTime joinedAt;
    private boolean favorite;
    private boolean archived;

    // Constructors
    public BoardMembership() {}

    public BoardMembership(Long boardId, Long userId, String role) {
        this.boardId = boardId;
        this.userId = userId;
        this.role = role != null ? role : "viewer";
    }

    // Getters and Setters
    public Long getBoardId() { return boardId; }
    public void setBoardId(Long boardId) { this.boardId = boardId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }


    // For backward compatibility with Participant naming
    public LocalDateTime getCreatedAt() { return joinedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.joinedAt = createdAt; }
}