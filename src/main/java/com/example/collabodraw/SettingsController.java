package com.example.collabodraw.whiteboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class SettingsController {

    @GetMapping("/settings")
    public String settings(Model model) {
        // **FIX: Add required model attributes for settings page**
        
        // Current user data
        model.addAttribute("currentUser", new Object() {
            public String getInitials() { return "JD"; }
            public String getFullName() { return "John Doe"; }
            public String getFirstName() { return "John"; }
            public String getLastName() { return "Doe"; }
            public String getEmail() { return "john.doe@example.com"; }
            public String getJobTitle() { return "UX Designer"; }
            public boolean isTwoFactorEnabled() { return false; }
            public String getBio() { return "Passionate UX Designer with 5+ years experience"; }
        });
        
        // User profile for form binding
        model.addAttribute("userProfile", new Object() {
            public String getFirstName() { return "John"; }
            public String getLastName() { return "Doe"; }
            public String getEmail() { return "john.doe@example.com"; }
            public String getJobTitle() { return "UX Designer"; }
            public String getBio() { return ""; }
        });
        
        // Mock team members (empty list for now)
        model.addAttribute("teamMembers", java.util.Collections.emptyList());
        
        return "settings";
    }

    // Handle profile updates
    @PostMapping("/settings/profile")
    public String updateProfile(@ModelAttribute("userProfile") Object userProfile, Model model) {
        // Handle profile update logic here
        model.addAttribute("message", "Profile updated successfully!");
        return "redirect:/settings";
    }

    // Legacy route support
    @GetMapping("/settings.html")
    public String settingsLegacy() {
        return "redirect:/settings";
    }
}
