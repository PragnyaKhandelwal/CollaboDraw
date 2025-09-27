package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    public String mainscreen(
            Authentication authentication, 
            Model model,
            @RequestParam(value = "board", required = false) String boardId,
            @RequestParam(value = "shared", required = false) String sharedBoardId,
            @RequestParam(value = "template", required = false) String templateId,
            @RequestParam(value = "preview", required = false) String previewId) {
        
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                model.addAttribute("currentUser", user);
            }
        }
        
        // Add parameters to model for JavaScript to use
        if (boardId != null) {
            model.addAttribute("boardId", boardId);
        }
        if (sharedBoardId != null) {
            model.addAttribute("sharedBoardId", sharedBoardId);
        }
        if (templateId != null) {
            model.addAttribute("templateId", templateId);
        }
        if (previewId != null) {
            model.addAttribute("previewId", previewId);
        }
        
        return "mainscreen"; // looks for src/main/resources/templates/mainscreen.html
    }
}
