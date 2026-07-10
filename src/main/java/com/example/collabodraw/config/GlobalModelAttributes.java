package com.example.collabodraw.config;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.model.entity.UserSettings;
import com.example.collabodraw.repository.UserSettingsRepository;
import com.example.collabodraw.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Injects the current user's theme preference into every page's model, so each of the 7
 * templates doesn't need its own controller code to look it up. The theme setting previously
 * saved to the database and was never read back anywhere - this is the read-back half of that
 * fix (see also static/css/theme-dark.css, and each template's <html data-theme="..."> attribute).
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final UserService userService;
    private final UserSettingsRepository userSettingsRepository;

    public GlobalModelAttributes(UserService userService, UserSettingsRepository userSettingsRepository) {
        this.userService = userService;
        this.userSettingsRepository = userSettingsRepository;
    }

    @ModelAttribute("pageTheme")
    public String pageTheme(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) return "system";
            User user = userService.findByUsername(authentication.getName());
            if (user == null) return "system";
            UserSettings settings = userSettingsRepository.findByUserId(user.getUserId());
            String theme = settings != null ? settings.getTheme() : null;
            return (theme == null || theme.isBlank()) ? "system" : theme;
        } catch (Exception e) {
            // Never let a theme lookup failure break page rendering.
            return "system";
        }
    }
}
