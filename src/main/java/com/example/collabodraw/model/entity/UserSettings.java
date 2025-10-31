package com.example.collabodraw.model.entity;

public class UserSettings {
    private Long userId;
    private String displayName;
    private String description;
    private String bio;
    private String theme; // light/dark/system
    private String language; // e.g., en
    private String timezone; // e.g., UTC
    private boolean emailNotifications;
    private boolean pushNotifications;
    private boolean boardUpdates;
    private boolean mentions;
    private boolean marketingEmails;
    private boolean twoFactorEnabled;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public boolean isEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(boolean emailNotifications) { this.emailNotifications = emailNotifications; }
    public boolean isPushNotifications() { return pushNotifications; }
    public void setPushNotifications(boolean pushNotifications) { this.pushNotifications = pushNotifications; }
    public boolean isBoardUpdates() { return boardUpdates; }
    public void setBoardUpdates(boolean boardUpdates) { this.boardUpdates = boardUpdates; }
    public boolean isMentions() { return mentions; }
    public void setMentions(boolean mentions) { this.mentions = mentions; }
    public boolean isMarketingEmails() { return marketingEmails; }
    public void setMarketingEmails(boolean marketingEmails) { this.marketingEmails = marketingEmails; }
    public boolean isTwoFactorEnabled() { return twoFactorEnabled; }
    public void setTwoFactorEnabled(boolean twoFactorEnabled) { this.twoFactorEnabled = twoFactorEnabled; }
}
