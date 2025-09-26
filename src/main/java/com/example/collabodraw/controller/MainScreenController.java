package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for mainscreen page
 */
@Controller
public class MainScreenController {

    private final UserService userService;

    public MainScreenController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/mainscreen")
    public String mainscreen(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                model.addAttribute("currentUser", user);
            }
        }
        return "mainscreen"; // looks for src/main/resources/templates/mainscreen.html
    }
}
