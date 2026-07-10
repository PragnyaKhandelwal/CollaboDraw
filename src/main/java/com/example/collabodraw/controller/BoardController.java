package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.service.DashboardRealtimeService;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(BoardController.class);

    private final UserService userService;
    private final WhiteboardService whiteboardService;
    private final DashboardRealtimeService dashboardRealtimeService;

    public BoardController(UserService userService, WhiteboardService whiteboardService,
                           DashboardRealtimeService dashboardRealtimeService) {
        this.userService = userService;
        this.whiteboardService = whiteboardService;
        this.dashboardRealtimeService = dashboardRealtimeService;
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

            // Load actual board data and check access
            Long numericBoardId = resolveBoardId(boardId);
            var board = whiteboardService.getWhiteboardById(numericBoardId);
            if (board == null) {
                return "redirect:/home?error=" + encodeMessage("Board not found");
            }

            String role = whiteboardService.getUserRoleInWhiteboard(
                    user != null ? user.getUserId() : null,
                    board.getBoardId());
            boolean isOwner = board.getOwnerId() != null && user != null && board.getOwnerId().equals(user.getUserId());
            boolean hasAccess = isOwner || (role != null);
            if (!hasAccess) {
                return "redirect:/home?error=" + encodeMessage("You do not have access to this board");
            }

            model.addAttribute("boardId", formatBoardId(board.getBoardId()));
            model.addAttribute("boardName", board.getBoardName());
            model.addAttribute("isShared", !isOwner);

            return "mainscreen";
        } catch (Exception e) {
            log.warn("Failed to load board {}: {}", boardId, e.getMessage());
            return "redirect:/home?error=" + encodeMessage("Failed to load board");
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

            Long numericBoardId = resolveBoardId(boardId);
            var board = whiteboardService.getWhiteboardById(numericBoardId);
            if (board == null) {
                return "redirect:/shared?error=" + encodeMessage("Shared board not found");
            }

            String role = whiteboardService.getUserRoleInWhiteboard(
                    user != null ? user.getUserId() : null,
                    board.getBoardId());
            boolean isOwner = board.getOwnerId() != null && user != null && board.getOwnerId().equals(user.getUserId());
            boolean hasAccess = isOwner || (role != null);
            if (!hasAccess) {
                return "redirect:/shared?error=" + encodeMessage("You do not have access to this shared board");
            }

            model.addAttribute("sharedBoardId", formatBoardId(board.getBoardId()));
            model.addAttribute("boardName", board.getBoardName());
            model.addAttribute("isShared", !isOwner);

            return "mainscreen";
        } catch (Exception e) {
            log.warn("Failed to load shared board {}: {}", boardId, e.getMessage());
            return "redirect:/shared?error=" + encodeMessage("Failed to load shared board");
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

            Long numericBoardId = resolveBoardId(boardId);
            var board = whiteboardService.getWhiteboardById(numericBoardId);
            if (board == null) {
                return "redirect:/my-content?error=" + encodeMessage("Board not found");
            }
            boolean isOwner = user != null && board.getOwnerId() != null && board.getOwnerId().equals(user.getUserId());
            if (!isOwner) {
                return "redirect:/my-content?error=" + encodeMessage("Only the owner can generate share links");
            }

            // Basic share link that opens the shared view; access is still enforced server-side
            model.addAttribute("boardId", formatBoardId(board.getBoardId()));
            model.addAttribute("shareUrl", "/boards/shared/open/" + formatBoardId(board.getBoardId()));

            return "redirect:/my-content?success=" + encodeMessage("Board shared successfully");
        } catch (Exception e) {
            log.warn("Failed to share board {}: {}", boardId, e.getMessage());
            return "redirect:/my-content?error=" + encodeMessage("Failed to share board");
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
            log.warn("Failed to duplicate board {}: {}", boardId, ex.getMessage());
            return "redirect:/my-content?error=" + encodeMessage("Failed to duplicate board");
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
            log.warn("Failed to delete board {}: {}", boardId, ex.getMessage());
            return "redirect:/my-content?error=" + encodeMessage("Failed to delete board");
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
            log.warn("Failed to copy shared board {}: {}", boardId, ex.getMessage());
            return "redirect:/shared?error=" + encodeMessage("Failed to copy shared board");
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
            log.warn("Failed to leave shared board {}: {}", boardId, ex.getMessage());
            return "redirect:/shared?error=" + encodeMessage("Failed to leave shared board");
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

                dashboardRealtimeService.publishBoardEvent(numericBoardId, "BOARD_DUPLICATED");
                dashboardRealtimeService.publishBoardEvent(duplicatedBoard.getBoardId(), "BOARD_CREATED");

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
            log.error("Failed to duplicate board {}", boardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Failed to duplicate board"));
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

            // Notify all board participants before deleting membership/board rows.
            dashboardRealtimeService.publishBoardEvent(numericBoardId, "BOARD_DELETING");
            whiteboardService.deleteBoard(numericBoardId, currentUser.getUserId());

            dashboardRealtimeService.publishUserEvent(currentUser.getUserId(), "BOARD_DELETED", "Board deleted");

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
            log.error("Failed to delete board {}", boardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Failed to delete board"));
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

                dashboardRealtimeService.publishBoardEvent(numericBoardId, "BOARD_COPIED");
                dashboardRealtimeService.publishBoardEvent(copiedBoard.getBoardId(), "BOARD_CREATED");

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
            log.error("Failed to copy shared board {}", boardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Failed to copy shared board"));
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

            dashboardRealtimeService.publishBoardEvent(numericBoardId, "MEMBER_LEFT");
            dashboardRealtimeService.publishUserEvent(currentUser.getUserId(), "BOARD_LEFT", "You left a shared board");

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
            log.error("Failed to leave board {}", boardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "message", "Failed to leave board"));
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