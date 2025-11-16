package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.model.dto.WhiteboardDto;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lightweight REST API to support frontend JS fetch() calls for boards
 */
@RestController
@RequestMapping("/api/boards")
public class BoardApiController {

    private final UserService userService;
    private final WhiteboardService whiteboardService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BoardApiController(UserService userService, WhiteboardService whiteboardService) {
        this.userService = userService;
        this.whiteboardService = whiteboardService;
    }

    /**
     * Minimal board info endpoint used by the frontend to validate existence and fetch name.
     */
    @GetMapping("/{boardId}")
    public ResponseEntity<Map<String, Object>> getBoardInfo(@PathVariable String boardId,
                                                            Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);

            Board board = whiteboardService.getWhiteboardById(numericBoardId);
            if (board == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "Board not found"));
            }
            boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(currentUser.getUserId());
            String role = whiteboardService.getUserRoleInWhiteboard(currentUser.getUserId(), board.getBoardId());
            boolean hasAccess = isOwner || (role != null);
            if (!hasAccess) {
                throw new AccessDeniedException("You do not have access to this board");
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", formatBoardId(board.getBoardId()),
                    "name", board.getBoardName()
            ));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to load board: " + ex.getMessage()));
        }
    }

    /**
     * Find-or-create a board by a user-entered session code so that the same code
     * always resolves to the same board across users and devices.
     * Body: { "code": "<sessionCode>" }
     */
    @PostMapping("/session")
    public ResponseEntity<Map<String, Object>> resolveSessionBoard(@RequestBody Map<String, Object> body,
                                                                   Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            if (body == null || !body.containsKey("code")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Missing 'code'"));
            }
            Object cobj = body.get("code");
            String code = (cobj instanceof String s) ? s.trim() : null;
            if (code == null || code.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Invalid session code"));
            }
            // Basic validation: allow letters, digits, dashes, underscores; length 2..40
            if (!code.matches("[A-Za-z0-9_-]{2,40}")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Session code must be 2-40 characters [A-Za-z0-9_-]"));
            }
            // Normalize to lowercase for stable mapping
            code = code.toLowerCase();

            Board board = whiteboardService.findOrCreateBoardBySessionCode(code, currentUser.getUserId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "id", formatBoardId(board.getBoardId()),
                    "name", board.getBoardName()
            ));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to resolve session: " + ex.getMessage()));
        }
    }

    @PostMapping("/new")
    public ResponseEntity<Map<String, Object>> createNewBoard(@RequestBody(required = false) Map<String, Object> body,
                                                              Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            String name = null;
            if (body != null) {
                Object n = body.get("name");
                if (n instanceof String s && !s.isBlank()) {
                    name = s.trim();
                }
            }
            if (name == null) name = "Untitled Board";

            WhiteboardDto dto = new WhiteboardDto(name, currentUser.getUserId(), false);
            Board created = whiteboardService.createWhiteboard(dto);
            // ensure owner membership
            whiteboardService.addUserToWhiteboard(created.getBoardId(), currentUser.getUserId(), "owner");

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "id", formatBoardId(created.getBoardId()),
                    "name", created.getBoardName()
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to create board: " + ex.getMessage()));
        }
    }

    @PostMapping("/share/{boardId}")
    public ResponseEntity<Map<String, Object>> shareBoardApi(@PathVariable String boardId,
                                                             Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);

            Board board = whiteboardService.getWhiteboardById(numericBoardId);
            if (board == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "Board not found"));
            }
            boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(currentUser.getUserId());
            if (!isOwner) {
                throw new AccessDeniedException("Only the owner can generate share links");
            }

            String shareUrl = "/boards/shared/open/" + formatBoardId(board.getBoardId());
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("message", "Board shared successfully");
            body.put("shareUrl", shareUrl);
            body.put("boardId", formatBoardId(board.getBoardId()));
            return ResponseEntity.ok(body);
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to share board: " + ex.getMessage()));
        }
    }

    @GetMapping("/{boardId}/content")
    public ResponseEntity<Map<String, Object>> getBoardContent(@PathVariable String boardId,
                                                               Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);
            Board board = whiteboardService.getWhiteboardById(numericBoardId);
            if (board == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "Board not found"));
            }
            boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(currentUser.getUserId());
            String role = whiteboardService.getUserRoleInWhiteboard(currentUser.getUserId(), board.getBoardId());
            boolean hasAccess = isOwner || (role != null);
            if (!hasAccess) throw new AccessDeniedException("You do not have access to this board");

            String snapshotJson = whiteboardService.getBoardSnapshot(numericBoardId);
            Map<String, Object> payload = new HashMap<>();
            payload.put("success", true);
            if (snapshotJson != null && !snapshotJson.isBlank()) {
                // snapshotJson expected to be a JSON with elements and settings
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(snapshotJson, Map.class);
                Object elements = data.get("elements");
                Object settings = data.get("settings");
                payload.put("elements", (elements instanceof String) ? elements : "");
                payload.put("settings", (settings instanceof Map) ? settings : new LinkedHashMap<>());
            } else {
                payload.put("elements", "");
                payload.put("settings", new LinkedHashMap<>());
            }
            return ResponseEntity.ok(payload);
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to load content: " + ex.getMessage()));
        }
    }

    @PostMapping("/{boardId}/content")
    public ResponseEntity<Map<String, Object>> saveBoardContent(@PathVariable String boardId,
                                                                @RequestBody Map<String, Object> body,
                                                                Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);
            Board board = whiteboardService.getWhiteboardById(numericBoardId);
            if (board == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "Board not found"));
            }
            boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(currentUser.getUserId());
            String role = whiteboardService.getUserRoleInWhiteboard(currentUser.getUserId(), board.getBoardId());
            boolean canWrite = isOwner || "editor".equalsIgnoreCase(role) || "owner".equalsIgnoreCase(role);
            if (!canWrite) throw new AccessDeniedException("You do not have write access to this board");

            // Build snapshot JSON
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("elements", body.getOrDefault("elements", ""));
            snapshot.put("settings", body.getOrDefault("settings", new LinkedHashMap<>()));
            String name = (String) body.get("name");
            String snapshotJson = objectMapper.writeValueAsString(snapshot);

            whiteboardService.saveBoardSnapshot(numericBoardId, currentUser.getUserId(), snapshotJson);
            if (name != null && !name.isBlank() && !name.equals(board.getBoardName())) {
                // Update name via repository method
                // Avoid direct repository access here; reuse service layer if added later.
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Board saved"
            ));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to save content: " + ex.getMessage()));
        }
    }
    @GetMapping("/open/{boardId}")
    public ResponseEntity<Map<String, Object>> openBoardApi(@PathVariable String boardId,
                                                            Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);

            Board board = whiteboardService.getWhiteboardById(numericBoardId);
            if (board == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "Board not found"));
            }

            boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(currentUser.getUserId());
            String role = whiteboardService.getUserRoleInWhiteboard(currentUser.getUserId(), board.getBoardId());
            boolean hasAccess = isOwner || (role != null);
            if (!hasAccess) {
                throw new AccessDeniedException("You do not have access to this board");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("success", true);
            payload.put("id", formatBoardId(board.getBoardId()));
            payload.put("name", board.getBoardName());
            // Elements/content persistence is not wired yet; return empty to let UI initialize
            payload.put("elements", "");
            payload.put("settings", new HashMap<>());
            return ResponseEntity.ok(payload);
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to open board: " + ex.getMessage()));
        }
    }

    @GetMapping("/shared/open/{boardId}")
    public ResponseEntity<Map<String, Object>> openSharedBoardApi(@PathVariable String boardId,
                                                                  Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            Long numericBoardId = resolveBoardId(boardId);

            Board board = whiteboardService.getWhiteboardById(numericBoardId);
            if (board == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "Shared board not found"));
            }

            boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(currentUser.getUserId());
            String role = whiteboardService.getUserRoleInWhiteboard(currentUser.getUserId(), board.getBoardId());
            boolean hasAccess = isOwner || (role != null);
            if (!hasAccess) {
                throw new AccessDeniedException("You do not have access to this shared board");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("success", true);
            payload.put("id", formatBoardId(board.getBoardId()));
            payload.put("name", board.getBoardName());
            payload.put("elements", "");
            payload.put("settings", new HashMap<>());
            payload.put("readOnly", true);
            return ResponseEntity.ok(payload);
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to open shared board: " + ex.getMessage()));
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

    private String formatBoardId(Long boardId) {
        return boardId != null ? "board-" + boardId : null;
        
    }
}
