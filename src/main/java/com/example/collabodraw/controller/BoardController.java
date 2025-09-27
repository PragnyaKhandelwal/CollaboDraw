package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.util.Map;
import java.nio.charset.StandardCharsets;

/**
 * Controller for board-related operations (CRUD, sharing, etc.)
 */
@Controller
@RequestMapping("/boards")
public class BoardController {

    private final UserService userService;
    private final WhiteboardService whiteboardService;

    public BoardController(UserService userService, WhiteboardService whiteboardService) {
        this.userService = userService;
        this.whiteboardService = whiteboardService;
    }

    /**
     * Open a board - Load board and redirect to mainscreen
     */
    @GetMapping("/open/{boardId}")
    public String openBoard(@PathVariable String boardId, Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/auth";
        }
        
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                model.addAttribute("currentUser", user);
            }
            
            // TODO: Load actual board data from database using whiteboardService
            // For now, pass board ID to mainscreen
            model.addAttribute("boardId", boardId);
            model.addAttribute("boardName", "Board " + boardId);
            
            return "mainscreen";
        } catch (Exception e) {
            // TODO: Add flash message for error handling
            return "redirect:/home?error=Failed to load board: " + e.getMessage();
        }
    }

    /**
     * Open a shared board - Load shared board and redirect to mainscreen
     */
    @GetMapping("/shared/open/{boardId}")
    public String openSharedBoard(@PathVariable String boardId, Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/auth";
        }
        
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                model.addAttribute("currentUser", user);
            }
            
            // TODO: Load actual shared board data from database using whiteboardService
            // For now, pass shared board ID to mainscreen
            model.addAttribute("sharedBoardId", boardId);
            model.addAttribute("boardName", "Shared Board " + boardId);
            model.addAttribute("isShared", true);
            
            return "mainscreen";
        } catch (Exception e) {
            return "redirect:/shared?error=Failed to load shared board: " + e.getMessage();
        }
    }

    /**
     * Share a board with other users - Show share interface
     */
    @GetMapping("/share/{boardId}")
    public String shareBoard(@PathVariable String boardId, Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/auth";
        }
        
        try {
            String username = authentication.getName();
            User user = userService.findByUsername(username);
            if (user != null) {
                model.addAttribute("currentUser", user);
            }
            
            // TODO: Implement board sharing logic
            model.addAttribute("boardId", boardId);
            model.addAttribute("shareUrl", "http://localhost:8080/boards/shared/open/" + boardId);
            model.addAttribute("successMessage", "Board shared successfully! Share the link below:");
            
            return "redirect:/my-content?success=" + encodeMessage("Board shared successfully");
        } catch (Exception e) {
            return "redirect:/my-content?error=" + encodeMessage("Failed to share board: " + e.getMessage());
        }
    }
    
    /**
     * Duplicate a board
     */
    @GetMapping("/duplicate/{boardId}")
    public String duplicateBoard(@PathVariable String boardId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/auth";
        }

        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);
            whiteboardService.duplicateBoard(numericBoardId, currentUser.getUserId());
            return "redirect:/my-content?success=" + encodeMessage("Board duplicated successfully");
        } catch (AccessDeniedException ex) {
            return "redirect:/my-content?error=" + encodeMessage(ex.getMessage());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return "redirect:/my-content?error=" + encodeMessage(ex.getMessage());
        } catch (Exception ex) {
            return "redirect:/my-content?error=" + encodeMessage("Failed to duplicate board: " + ex.getMessage());
        }
    }
    
    /**
     * Delete a board
     */
    @GetMapping("/delete/{boardId}")
    public String deleteBoard(@PathVariable String boardId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/auth";
        }

        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);
            whiteboardService.deleteBoard(numericBoardId, currentUser.getUserId());
            return "redirect:/my-content?success=" + encodeMessage("Board deleted successfully");
        } catch (AccessDeniedException ex) {
            return "redirect:/my-content?error=" + encodeMessage(ex.getMessage());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return "redirect:/my-content?error=" + encodeMessage(ex.getMessage());
        } catch (Exception ex) {
            return "redirect:/my-content?error=" + encodeMessage("Failed to delete board: " + ex.getMessage());
        }
    }
    
    /**
     * Copy a shared board to user's collection
     */
    @GetMapping("/copy-shared/{boardId}")
    public String copySharedBoard(@PathVariable String boardId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/auth";
        }

        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);
            whiteboardService.copySharedBoard(numericBoardId, currentUser.getUserId());
            return "redirect:/my-content?success=" + encodeMessage("Shared board copied to your collection");
        } catch (AccessDeniedException ex) {
            return "redirect:/shared?error=" + encodeMessage(ex.getMessage());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return "redirect:/shared?error=" + encodeMessage(ex.getMessage());
        } catch (Exception ex) {
            return "redirect:/shared?error=" + encodeMessage("Failed to copy shared board: " + ex.getMessage());
        }
    }
    
    /**
     * Leave a shared board
     */
    @GetMapping("/leave/{boardId}")
    public String leaveBoard(@PathVariable String boardId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/auth";
        }

        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);
            whiteboardService.leaveBoard(numericBoardId, currentUser.getUserId());
            return "redirect:/shared?success=" + encodeMessage("Left shared board successfully");
        } catch (AccessDeniedException ex) {
            return "redirect:/shared?error=" + encodeMessage(ex.getMessage());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return "redirect:/shared?error=" + encodeMessage(ex.getMessage());
        } catch (Exception ex) {
            return "redirect:/shared?error=" + encodeMessage("Failed to leave shared board: " + ex.getMessage());
        }
    }

    /**
     * Duplicate a board
     */
    @PostMapping("/duplicate/{boardId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> duplicateBoardApi(
            @PathVariable String boardId,
            Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);
            com.example.collabodraw.model.entity.Board duplicatedBoard =
                    whiteboardService.duplicateBoard(numericBoardId, currentUser.getUserId());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Board duplicated successfully",
                "newBoardId", formatBoardId(duplicatedBoard.getBoardId())
            ));

        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Failed to duplicate board: " + e.getMessage()));
        }
    }

    /**
     * Delete a board
     */
    @DeleteMapping("/delete/{boardId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteBoardApi(
            @PathVariable String boardId,
            Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);
            whiteboardService.deleteBoard(numericBoardId, currentUser.getUserId());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Board deleted successfully"
            ));

        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Failed to delete board: " + e.getMessage()));
        }
    }

    /**
     * Copy a shared board to user's own boards
     */
    @PostMapping("/copy-shared/{boardId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> copySharedBoardApi(
            @PathVariable String boardId,
            Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);
            com.example.collabodraw.model.entity.Board copiedBoard =
                    whiteboardService.copySharedBoard(numericBoardId, currentUser.getUserId());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Shared board copied successfully",
                "newBoardId", formatBoardId(copiedBoard.getBoardId())
            ));

        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Failed to copy shared board: " + e.getMessage()));
        }
    }

    /**
     * Leave a shared board
     */
    @PostMapping("/leave/{boardId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> leaveBoardApi(
            @PathVariable String boardId,
            Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);
            whiteboardService.leaveBoard(numericBoardId, currentUser.getUserId());

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Left board successfully"
            ));

        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Failed to leave board: " + e.getMessage()));
        }
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User must be authenticated");
        }
        String username = authentication.getName();
        User currentUser = userService.findByUsername(username);
        if (currentUser == null) {
            throw new IllegalStateException("User not found");
        }
        return currentUser;
    }

    private Long resolveBoardId(String boardId) {
        if (boardId == null || boardId.isBlank()) {
            throw new IllegalArgumentException("Board ID is required");
        }
        String trimmed = boardId.trim();
        if (trimmed.startsWith("board-")) {
            trimmed = trimmed.substring("board-".length());
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid board ID format: " + boardId);
        }
    }

    private String encodeMessage(String message) {
        return UriUtils.encode(message, StandardCharsets.UTF_8);
    }

    private String formatBoardId(Long boardId) {
        return boardId != null ? "board-" + boardId : null;
    }
}