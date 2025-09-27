package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for home page and main application features
 */
@Controller
public class HomeController {

    private final UserService userService;
    private final WhiteboardService whiteboardService;

    public HomeController(UserService userService, WhiteboardService whiteboardService) {
        this.userService = userService;
        this.whiteboardService = whiteboardService;
    }

    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            
            try {
                // Get user information
                User currentUser = userService.findByUsername(username);
                if (currentUser != null) {
                    model.addAttribute("currentUser", currentUser);
                    
                    // Get user's whiteboards
                    var whiteboards = whiteboardService.getWhiteboardsByOwner(currentUser.getUserId());
                    model.addAttribute("whiteboards", whiteboards);
                } else {
                    // Fallback if user not found
                    model.addAttribute("username", username);
                    model.addAttribute("whiteboards", java.util.Collections.emptyList());
                }
            } catch (Exception e) {
                // Log error and provide fallback
                System.err.println("Error loading user data: " + e.getMessage());
                model.addAttribute("username", username);
                model.addAttribute("whiteboards", java.util.Collections.emptyList());
            }
        }
        return "home";
    }
}
