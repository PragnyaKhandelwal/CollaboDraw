package com.example.collabodraw.model;

/**
 * User profile form object for settings page
 */
public class UserProfile {
    private String username;
    private String displayName;
    private String email;
    private String description;
    private String bio;

    // Constructors
    public UserProfile() {}

    public UserProfile(String username, String displayName, String email, String description, String bio) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.description = description;
        this.bio = bio;
    }

    // Getters and Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}