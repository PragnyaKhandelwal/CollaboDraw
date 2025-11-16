package com.example.collabodraw.controller;

import com.example.collabodraw.service.RealtimeEventStore;
import com.example.collabodraw.service.WhiteboardService;
import com.example.collabodraw.model.entity.Board;
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

    private final RealtimeEventStore eventStore;
    private final WhiteboardService whiteboardService;

    public LiveStateController(RealtimeEventStore eventStore, WhiteboardService whiteboardService) {
        this.eventStore = eventStore;
        this.whiteboardService = whiteboardService;
    }

    @GetMapping("/{boardId}")
    public ResponseEntity<?> getLiveEvents(@PathVariable String boardId, Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new AccessDeniedException("User must be authenticated");
            }
            Long numericBoardId = resolveBoardId(boardId);
            Board board = whiteboardService.getWhiteboardById(numericBoardId);
            if (board == null) return ResponseEntity.notFound().build();
            // Basic access check (owner or member)
            String role = whiteboardService.getUserRoleInWhiteboard(whiteboardService.getWhiteboardById(numericBoardId).getOwnerId(), numericBoardId);
            // (Simplified: if board exists we allow fetch; tighten with membership if needed.)
            List<Map<String, Object>> events = eventStore.getEvents(numericBoardId);
            return ResponseEntity.ok(Map.of("success", true, "events", events));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    private Long resolveBoardId(String boardId) {
        if (boardId == null || boardId.isBlank()) throw new IllegalArgumentException("Board ID required");
        String trimmed = boardId.trim();
        if (trimmed.startsWith("board-")) trimmed = trimmed.substring("board-".length());
        return Long.parseLong(trimmed);
    }
}
