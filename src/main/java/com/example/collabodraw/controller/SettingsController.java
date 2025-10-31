package com.example.collabodraw.controller;

import com.example.collabodraw.model.UserProfile;
import com.example.collabodraw.model.entity.Notification;
import com.example.collabodraw.model.entity.Team;
import com.example.collabodraw.model.entity.TeamMember;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.model.entity.UserSettings;
import com.example.collabodraw.service.NotificationService;
import com.example.collabodraw.service.SettingsService;
import com.example.collabodraw.service.TeamService;
import com.example.collabodraw.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class SettingsController {

    private final UserService userService;
    private final SettingsService settingsService;
    private final TeamService teamService;
    private final NotificationService notificationService;

    public SettingsController(UserService userService, SettingsService settingsService, TeamService teamService, NotificationService notificationService) {
        this.userService = userService;
        this.settingsService = settingsService;
        this.teamService = teamService;
        this.notificationService = notificationService;
    }

    @GetMapping("/settings")
    public String settings(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                model.addAttribute("currentUser", user);

                // Profile form object
                UserProfile userProfile = new UserProfile();
                userProfile.setUsername(user.getUsername());
                userProfile.setEmail(user.getEmail());
                userProfile.setDisplayName(user.getUsername());
                model.addAttribute("userProfile", userProfile);

                // User Settings (preferences)
                UserSettings prefs = settingsService.getOrInitSettings(user.getUserId(), user.getUsername());
                model.addAttribute("userSettings", prefs);

                // Team & members
                Team team = teamService.getOrCreatePersonalTeam(user.getUserId());
                List<TeamMember> members = teamService.members(team.getTeamId());
                model.addAttribute("team", team);
                model.addAttribute("teamMembers", members);

                // Notifications
                List<Notification> notifications = notificationService.recentForUser(user.getUserId());
                model.addAttribute("notifications", notifications);
            }
        }
        return "settings";
    }

    @PostMapping("/settings/profile")
    public String updateProfile(@ModelAttribute UserProfile userProfile, Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                user.setUsername(userProfile.getUsername());
                user.setEmail(userProfile.getEmail());
                try {
                    userService.updateUser(user);
                    // Also persist meta into user_settings
                    UserSettings prefs = settingsService.getOrInitSettings(user.getUserId(), user.getUsername());
                    prefs.setDisplayName(userProfile.getDisplayName());
                    prefs.setDescription(userProfile.getDescription());
                    prefs.setBio(userProfile.getBio());
                    settingsService.update(prefs);
                    model.addAttribute("successMessage", "Profile updated successfully!");
                } catch (Exception e) {
                    model.addAttribute("errorMessage", "Failed to update profile: " + e.getMessage());
                }
                model.addAttribute("currentUser", user);
                model.addAttribute("userProfile", userProfile);
                // refresh settings and team/notifications
                model.addAttribute("userSettings", settingsService.getOrInitSettings(user.getUserId(), user.getUsername()));
                Team team = teamService.getOrCreatePersonalTeam(user.getUserId());
                model.addAttribute("team", team);
                model.addAttribute("teamMembers", teamService.members(team.getTeamId()));
                model.addAttribute("notifications", notificationService.recentForUser(user.getUserId()));
            }
        }
        return "settings";
    }

    @PostMapping("/settings/preferences")
    public String updatePreferences(@ModelAttribute("userSettings") UserSettings userSettings, Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                userSettings.setUserId(user.getUserId());
                settingsService.update(userSettings);
                model.addAttribute("successMessage", "Preferences updated");
            }
        }
        return settings(authentication, model);
    }

    @PostMapping("/settings/notifications/{id}/read")
    public String markNotificationRead(@PathVariable("id") Long id, Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            User user = userService.findByUsername(authentication.getName());
            if (user != null) notificationService.markRead(user.getUserId(), id);
        }
        return settings(authentication, model);
    }

    @PostMapping("/settings/team/invite")
    public String inviteToTeam(@RequestParam(value = "email", required = false) String email,
                               @RequestParam(value = "username", required = false) String inviteUsername,
                               @RequestParam(value = "role", defaultValue = "member") String role,
                               Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            User current = userService.findByUsername(authentication.getName());
            if (current != null) {
                User target = null;
                if (email != null && !email.isBlank()) {
                    target = userService.findByEmail(email);
                }
                if (target == null && inviteUsername != null && !inviteUsername.isBlank()) {
                    target = userService.findByUsername(inviteUsername);
                }
                if (target != null) {
                    Team team = teamService.getOrCreatePersonalTeam(current.getUserId());
                    teamService.addMember(team.getTeamId(), target.getUserId(), role);
                    notificationService.create(target.getUserId(), "invite", "Team invite",
                            current.getUsername() + " added you to their team", null, null);
                    model.addAttribute("successMessage", "Invited " + (target.getUsername() != null ? target.getUsername() : target.getEmail()));
                } else {
                    model.addAttribute("errorMessage", "User not found for invite");
                }
            }
        }
        return settings(authentication, model);
    }

    @PostMapping("/settings/team/{userId}/remove")
    public String removeFromTeam(@PathVariable("userId") Long userId,
                                 Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            User current = userService.findByUsername(authentication.getName());
            if (current != null) {
                Team team = teamService.getOrCreatePersonalTeam(current.getUserId());
                teamService.removeMember(team.getTeamId(), userId);
                model.addAttribute("successMessage", "Removed member from team");
            }
        }
        return settings(authentication, model);
    }
}
