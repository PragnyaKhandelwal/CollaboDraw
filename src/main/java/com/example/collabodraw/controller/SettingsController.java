package com.example.collabodraw.controller;

import com.example.collabodraw.model.UserProfile;
import com.example.collabodraw.model.entity.Notification;
import com.example.collabodraw.model.entity.Team;
import com.example.collabodraw.model.entity.TeamMember;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.model.entity.UserSettings;
import com.example.collabodraw.repository.ActivityLogRepository;
import com.example.collabodraw.repository.SessionRepository;
import com.example.collabodraw.service.NotificationService;
import com.example.collabodraw.service.SettingsService;
import com.example.collabodraw.service.TeamService;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class SettingsController {

    private final UserService userService;
    private final SettingsService settingsService;
    private final TeamService teamService;
    private final NotificationService notificationService;
    private final SessionRepository sessionRepository;
    private final ActivityLogRepository activityLogRepository;
    private final WhiteboardService whiteboardService;

    public SettingsController(UserService userService, SettingsService settingsService, TeamService teamService,
                              NotificationService notificationService, SessionRepository sessionRepository,
                              ActivityLogRepository activityLogRepository, WhiteboardService whiteboardService) {
        this.userService = userService;
        this.settingsService = settingsService;
        this.teamService = teamService;
        this.notificationService = notificationService;
        this.sessionRepository = sessionRepository;
        this.activityLogRepository = activityLogRepository;
        this.whiteboardService = whiteboardService;
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
                UserSettings existing = settingsService.getOrInitSettings(user.getUserId(), user.getUsername());
                existing.setEmailNotifications(userSettings.isEmailNotifications());
                existing.setPushNotifications(userSettings.isPushNotifications());
                existing.setBoardUpdates(userSettings.isBoardUpdates());
                existing.setMentions(userSettings.isMentions());
                existing.setMarketingEmails(userSettings.isMarketingEmails());
                settingsService.update(existing);
                model.addAttribute("successMessage", "Preferences updated");
            }
        }
        return settings(authentication, model);
    }

    @PostMapping("/settings/advanced")
    public String updateAdvanced(@ModelAttribute("userSettings") UserSettings userSettings, Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            User user = userService.findByUsername(authentication.getName());
            if (user != null) {
                UserSettings existing = settingsService.getOrInitSettings(user.getUserId(), user.getUsername());
                existing.setTheme(userSettings.getTheme());
                existing.setLanguage(userSettings.getLanguage());
                existing.setTimezone(userSettings.getTimezone());
                existing.setTwoFactorEnabled(userSettings.isTwoFactorEnabled());
                settingsService.update(existing);
                model.addAttribute("successMessage", "Advanced settings updated");
                // GlobalModelAttributes.pageTheme() already ran earlier in this request, before
                // the save above, so it's holding the pre-save value - override it here so the
                // page that renders from this same response reflects the theme immediately
                // instead of only taking effect after the next navigation.
                model.addAttribute("pageTheme", (existing.getTheme() == null || existing.getTheme().isBlank()) ? "system" : existing.getTheme());
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

    /**
     * Profile picture upload. The "Change" button previously just showed a fake "updated!"
     * alert with no upload at all - this stores the (client-resized) image as a data: URL,
     * the same pattern already used for canvas snapshots (see DrawingController), so it needs
     * no new file-storage infrastructure.
     */
    @PostMapping("/api/settings/avatar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateAvatar(@RequestBody Map<String, String> body, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "Unauthorized"));
        }
        User user = userService.findByUsername(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("success", false, "message", "User not found"));
        }
        String dataUrl = body != null ? body.get("dataUrl") : null;
        if (dataUrl == null || dataUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No image provided"));
        }
        if (!dataUrl.startsWith("data:image/")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Image must be a data: URL"));
        }
        // Client resizes to a small avatar before upload, but don't trust that - cap the
        // stored payload so a crafted request can't stuff megabytes into this row.
        if (dataUrl.length() > 500_000) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Image is too large"));
        }

        settingsService.updateAvatar(user.getUserId(), dataUrl);
        return ResponseEntity.ok(Map.of("success", true, "avatarData", dataUrl));
    }

    @GetMapping("/api/settings/collaboration/{boardId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> collaborationSummary(@PathVariable("boardId") Long boardId,
                                                                    Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Unauthorized"));
        }

        User user = userService.findByUsername(authentication.getName());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found"));
        }

        var board = whiteboardService.getWhiteboardById(boardId);
        if (board == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Board not found"));
        }

        String role = whiteboardService.getUserRoleInWhiteboard(user.getUserId(), boardId);
        boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(user.getUserId());
        if (!isOwner && role == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "No access to this board"));
        }

        int activeConnections = sessionRepository.activeConnectionCount(boardId);
        var participants = sessionRepository.activeParticipants(boardId);
        int activity24h = activityLogRepository.countRecentActivityForBoard(boardId, 24);
        String latest = activityLogRepository.latestActivityTextForBoard(boardId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("success", true);
        payload.put("boardId", boardId);
        payload.put("boardName", board.getBoardName());
        payload.put("lastModified", board.getLastModified());
        payload.put("activeConnections", activeConnections);
        payload.put("participants", participants);
        payload.put("activity24h", activity24h);
        payload.put("latestActivity", latest != null ? latest : "No recent activity");
        return ResponseEntity.ok(payload);
    }
}
