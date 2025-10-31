package com.example.collabodraw.model.dto;

public class Participant {
    private Long userId;
    private String username;

    public Participant() {}
    public Participant(Long userId, String username) {
        this.userId = userId; this.username = username;
    }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}