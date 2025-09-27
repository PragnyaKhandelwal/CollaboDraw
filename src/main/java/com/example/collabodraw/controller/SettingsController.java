package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.model.UserProfile;
import com.example.collabodraw.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class SettingsController {
    
    private final UserService userService;
    
    public SettingsController(UserService userService) {
        this.userService = userService;
    }
    
    @GetMapping("/settings")
    public String settings(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                model.addAttribute("currentUser", user);
                
                // Create user profile form object
                UserProfile userProfile = new UserProfile();
                userProfile.setUsername(user.getUsername());
                userProfile.setEmail(user.getEmail());
                userProfile.setDisplayName(user.getUsername()); // Default to username
                userProfile.setDescription(""); // TODO: Add description field to User entity
                userProfile.setBio(""); // TODO: Add bio field to User entity
                
                model.addAttribute("userProfile", userProfile);
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
                // Update user profile
                user.setUsername(userProfile.getUsername());
                user.setEmail(userProfile.getEmail());
                // TODO: Update additional fields when they're added to User entity
                
                try {
                    userService.updateUser(user);
                    model.addAttribute("successMessage", "Profile updated successfully!");
                } catch (Exception e) {
                    model.addAttribute("errorMessage", "Failed to update profile: " + e.getMessage());
                }
                
                model.addAttribute("currentUser", user);
                model.addAttribute("userProfile", userProfile);
            }
        }
        return "settings";
    }
}
