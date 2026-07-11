package com.example.collabodraw.controller;

import com.example.collabodraw.realtime.EventStore;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoint to retrieve live collaboration element events so late joiners
 * can reconstruct the current board state (strokes, notes, text, etc.).
 */
@RestController
@RequestMapping("/api/live")
public class LiveStateController {

    private final EventStore eventStore;
    private final WhiteboardService whiteboardService;
    private final UserService userService;

    public LiveStateController(EventStore eventStore, WhiteboardService whiteboardService, UserService userService) {
        this.eventStore = eventStore;
        this.whiteboardService = whiteboardService;
        this.userService = userService;
    }

    @GetMapping("/{boardId}")
    public ResponseEntity<?> getLiveEvents(@PathVariable String boardId, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new AccessDeniedException("User must be authenticated");
            }
            User currentUser = userService.findByUsername(authentication.getName());
            if (currentUser == null) {
                throw new AccessDeniedException("User not found");
            }
            Long numericBoardId = resolveBoardId(boardId);
            Board board = whiteboardService.getWhiteboardById(numericBoardId);
            if (board == null) return ResponseEntity.notFound().build();

            boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(currentUser.getUserId());
            String role = whiteboardService.getUserRoleInWhiteboard(currentUser.getUserId(), numericBoardId);
            if (!isOwner && role == null) {
                throw new AccessDeniedException("You do not have access to this board");
            }

            List<Map<String, Object>> events = eventStore.getEvents(numericBoardId);
            return ResponseEntity.ok(Map.of("success", true, "events", events));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to load live events"));
        }
    }

    private Long resolveBoardId(String boardId) {
        if (boardId == null || boardId.isBlank()) throw new IllegalArgumentException("Board ID required");
        String trimmed = boardId.trim();
        if (trimmed.startsWith("board-")) trimmed = trimmed.substring("board-".length());
        return Long.parseLong(trimmed);
    }
}
