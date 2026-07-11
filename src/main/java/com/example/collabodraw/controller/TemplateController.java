package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.model.dto.WhiteboardDto;
import com.example.collabodraw.service.DashboardRealtimeService;
import com.example.collabodraw.service.TemplateService;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for template-related operations
 */
@Controller
@RequestMapping("/templates")
public class TemplateController {

    private final UserService userService;
    private final TemplateService templateService;
    private final WhiteboardService whiteboardService;
    private final DashboardRealtimeService dashboardRealtimeService;

    public TemplateController(UserService userService, TemplateService templateService,
                              WhiteboardService whiteboardService,
                              DashboardRealtimeService dashboardRealtimeService) {
        this.userService = userService;
        this.templateService = templateService;
        this.whiteboardService = whiteboardService;
        this.dashboardRealtimeService = dashboardRealtimeService;
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
        model.addAttribute("totalTemplates", templateService.getTotalTemplateCount());
        model.addAttribute("categories", templateService.getDistinctCategories());

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

        User user = userService.findByUsername(authentication.getName());
        if (user == null) {
            return "redirect:/auth";
        }

        // Increment usage when a template is used and create a dedicated board for that template.
        try { templateService.incrementUsage(templateKey); } catch (Exception ignored) {}

        String templateName = "Template";
        var template = templateService.getTemplateByKey(templateKey);
        if (template != null && template.getName() != null && !template.getName().isBlank()) {
            templateName = template.getName();
        }

        WhiteboardDto dto = new WhiteboardDto();
        dto.setOwnerId(user.getUserId());
        dto.setName(templateName + " Board");
        dto.setIsPublic(false);

        var board = whiteboardService.createWhiteboard(dto);
        whiteboardService.addUserToWhiteboard(board.getBoardId(), user.getUserId(), "owner");
        dashboardRealtimeService.publishBoardEvent(board.getBoardId(), "BOARD_CREATED");

        return "redirect:/mainscreen?board=" + board.getBoardId() + "&template=" + templateKey + "&seedTemplate=1";
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