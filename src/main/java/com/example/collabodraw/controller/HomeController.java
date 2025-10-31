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

    // Root mapping - redirects to home page
    @GetMapping("/")
    public String root() {
        return "redirect:/home";
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
        } else {
            // Unauthenticated: still show real stats from DB when possible
            try {
                var allBoards = whiteboardService.getAllWhiteboards();
                model.addAttribute("totalBoards", allBoards != null ? allBoards.size() : 0);
            } catch (Exception e) {
                model.addAttribute("totalBoards", 0);
            }
            // Recent/shared can be refined later; keep minimal non-hardcoded defaults
            model.addAttribute("recentBoards", 0);
            model.addAttribute("sharedBoards", 0);
            model.addAttribute("templates", 0);
        }
        return "home";
    }

    // Dashboard redirect (alternative)
    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/home";
    }

    // Legacy route support
    @GetMapping("/home.html")
    public String homeLegacy() {
        return "redirect:/home";
    }
}
