package com.example.collabodraw.controller;

import com.example.collabodraw.model.dto.WhiteboardDto;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.Template;
import com.example.collabodraw.service.DashboardRealtimeService;
import com.example.collabodraw.service.TemplateService;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for home page and main application features
 */
@Controller
public class HomeController {

    private final UserService userService;
    private final WhiteboardService whiteboardService;
    private final TemplateService templateService;
    private final DashboardRealtimeService dashboardRealtimeService;

    public HomeController(UserService userService, WhiteboardService whiteboardService,
                          TemplateService templateService, DashboardRealtimeService dashboardRealtimeService) {
        this.userService = userService;
        this.whiteboardService = whiteboardService;
        this.templateService = templateService;
        this.dashboardRealtimeService = dashboardRealtimeService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String home(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            try {
                User currentUser = userService.findByUsername(username);
                if (currentUser != null) {
                    model.addAttribute("currentUser", currentUser);
                    var whiteboards = whiteboardService.getAccessibleWhiteboards(currentUser.getUserId());
                    model.addAttribute("whiteboards", whiteboards);
                    model.addAttribute("popularTemplates", templateService.getPopularTemplates(10));
                } else {
                    model.addAttribute("username", username);
                    model.addAttribute("whiteboards", java.util.Collections.emptyList());
                    model.addAttribute("popularTemplates", templateService.getPopularTemplates(10));
                }
            } catch (Exception e) {
                System.err.println("Error loading user data: " + e.getMessage());
                model.addAttribute("username", username);
                model.addAttribute("whiteboards", java.util.Collections.emptyList());
                model.addAttribute("popularTemplates", templateService.getPopularTemplates(10));
            }
        } else {
            try {
                var allBoards = whiteboardService.getAllWhiteboards();
                model.addAttribute("totalBoards", allBoards != null ? allBoards.size() : 0);
            } catch (Exception e) {
                model.addAttribute("totalBoards", 0);
            }
            model.addAttribute("recentBoards", 0);
            model.addAttribute("sharedBoards", 0);
            model.addAttribute("templates", 0);
            model.addAttribute("popularTemplates", templateService.getPopularTemplates(10));
        }
        return "home";
    }

    @GetMapping("/api/home/dashboard-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> dashboardData(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            User user = userService.findByUsername(authentication.getName());
            if (user == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            var boards = whiteboardService.getAccessibleWhiteboards(user.getUserId());
            var templates = templateService.getPopularTemplates(10);

            response.put("success", true);
            response.put("boards", boards);
            response.put("templates", templates);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ✅ FIXED: Use createWhiteboard() with WhiteboardDto instead
    @PostMapping("/api/boards/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createBoard(
            @RequestParam String name,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                response.put("success", false);
                response.put("message", "User not authenticated");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            
            if (user == null) {
                response.put("success", false);
                response.put("message", "User not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            // ✅ Use WhiteboardDto with existing createWhiteboard method
            String boardName = name != null && !name.trim().isEmpty() ? name : "Untitled Board";
            WhiteboardDto whiteboardDto = new WhiteboardDto();
            whiteboardDto.setOwnerId(user.getUserId());
            whiteboardDto.setName(boardName);
            whiteboardDto.setIsPublic(false);
            
            // Save using existing createWhiteboard method
            Board savedBoard = whiteboardService.createWhiteboard(whiteboardDto);
            whiteboardService.addUserToWhiteboard(savedBoard.getBoardId(), user.getUserId(), "owner");
            
            response.put("success", true);
            response.put("id", savedBoard.getBoardId());
            response.put("name", savedBoard.getBoardName());

            dashboardRealtimeService.publishBoardEvent(savedBoard.getBoardId(), "BOARD_CREATED");
            dashboardRealtimeService.publishUserEvent(user.getUserId(), "BOARD_CREATED", "Board created");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error creating board: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/home";
    }

    @GetMapping("/home.html")
    public String homeLegacy() {
        return "redirect:/home";
    }
}
