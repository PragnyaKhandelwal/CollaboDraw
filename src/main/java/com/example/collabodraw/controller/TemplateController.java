package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
// Removed unused imports (HttpStatus, ResponseEntity, ResponseBody, Map related)

/**
 * Controller for template-related operations
 */
@Controller
@RequestMapping("/templates")
public class TemplateController {

    private final UserService userService;

    public TemplateController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Show templates gallery page
     */
    @GetMapping
    public String templates(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                model.addAttribute("currentUser", user);
            }
        }

        // TODO: Add template data from database
        // For now, we'll let the template handle sample data

        return "templates";
    }

    /**
     * Use a template to create a new board - Load template and redirect to mainscreen
     */
    @GetMapping("/use/{templateId}")
    public String useTemplate(@PathVariable String templateId, Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/auth";
        }
        
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                model.addAttribute("currentUser", user);
            }
            
            // TODO: Load actual template data from database
            // For now, pass template ID to mainscreen
            model.addAttribute("templateId", templateId);
            model.addAttribute("boardName", "New Board from Template " + templateId);
            
            return "mainscreen";
        } catch (Exception e) {
            return "redirect:/templates?error=Failed to load template: " + e.getMessage();
        }
    }

    /**
     * Preview a template - Load template in preview mode and redirect to mainscreen
     */
    @GetMapping("/preview/{templateId}")
    public String previewTemplate(@PathVariable String templateId, Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/auth";
        }
        
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                model.addAttribute("currentUser", user);
            }
            
            // TODO: Load actual template data from database
            // For now, pass preview template ID to mainscreen
            model.addAttribute("previewId", templateId);
            model.addAttribute("boardName", "Template " + templateId + " (Preview)");
            model.addAttribute("isPreview", true);
            
            return "mainscreen";
        } catch (Exception e) {
            return "redirect:/templates?error=Failed to load template preview: " + e.getMessage();
        }
    }
}