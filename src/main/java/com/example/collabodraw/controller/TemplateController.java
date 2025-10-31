package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.service.TemplateService;
import com.example.collabodraw.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * Controller for template-related operations
 */
@Controller
@RequestMapping("/templates")
public class TemplateController {

    private final UserService userService;
    private final TemplateService templateService;

    public TemplateController(UserService userService, TemplateService templateService) {
        this.userService = userService;
        this.templateService = templateService;
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

        model.addAttribute("templates", templateService.getAllTemplates());
        Map<String, Integer> counts = templateService.getCategoryCounts();
        model.addAttribute("totalTemplates", counts.getOrDefault("all", 0));
        model.addAttribute("popularTemplates", counts.getOrDefault("popular", 0));
        model.addAttribute("businessTemplates", counts.getOrDefault("business", 0));
        model.addAttribute("designTemplates", counts.getOrDefault("design", 0));
        model.addAttribute("educationTemplates", counts.getOrDefault("education", 0));
        model.addAttribute("planningTemplates", counts.getOrDefault("planning", 0));

        return "templates";
    }

    // Legacy route support
    @GetMapping(".html")
    public String templatesLegacy() {
        return "redirect:/templates";
    }

    /**
     * Use a template to create a new board - Redirect to mainscreen with query param
     */
    @GetMapping("/use/{templateKey}")
    public String useTemplate(@PathVariable String templateKey, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/auth";
        }
        // Increment usage when a template is used to create a board
        try { templateService.incrementUsage(templateKey); } catch (Exception ignored) {}
        return "redirect:/mainscreen?template=" + templateKey;
    }

    /**
     * Preview a template - open mainscreen in preview mode
     */
    @GetMapping("/preview/{templateKey}")
    public String previewTemplate(@PathVariable String templateKey, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/auth";
        }
        return "redirect:/mainscreen?template=" + templateKey + "&preview=1";
    }
}