package com.example.collabodraw.model.entity;

import java.time.LocalDateTime;

/**
 * User entity representing a user in the system
 * Maps to 'users' table in collaborative_workspace_db
 */
public class User {
    private Long userId;
    private String username;
    private String email;
    private String passwordHash;
    private LocalDateTime createdAt;

    // Constructors
    public User() {}

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    // For backward compatibility, keep getId/setId methods that delegate to userId
    public Long getId() { return userId; }
    public void setId(Long id) { this.userId = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
