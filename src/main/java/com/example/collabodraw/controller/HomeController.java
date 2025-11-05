package com.example.collabodraw.controller;

import com.example.collabodraw.model.dto.WhiteboardDto;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.model.entity.Board;
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

    public HomeController(UserService userService, WhiteboardService whiteboardService) {
        this.userService = userService;
        this.whiteboardService = whiteboardService;
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
                    var whiteboards = whiteboardService.getWhiteboardsByOwner(currentUser.getUserId());
                    model.addAttribute("whiteboards", whiteboards);
                } else {
                    model.addAttribute("username", username);
                    model.addAttribute("whiteboards", java.util.Collections.emptyList());
                }
            } catch (Exception e) {
                System.err.println("Error loading user data: " + e.getMessage());
                model.addAttribute("username", username);
                model.addAttribute("whiteboards", java.util.Collections.emptyList());
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
        }
        return "home";
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
            
            response.put("success", true);
            response.put("id", savedBoard.getBoardId());
            response.put("name", savedBoard.getBoardName());
            
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
