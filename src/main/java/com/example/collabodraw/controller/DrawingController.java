package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.Element;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.repository.ElementRepository;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Canvas snapshot endpoints. Every route here reads or overwrites a board's
 * drawing data, so every route requires an authenticated caller who is at
 * least a member of that board (editors/owners to write, any member to read).
 */
@RestController
@RequestMapping("/api/drawings")
public class DrawingController {

    private static final Logger log = LoggerFactory.getLogger(DrawingController.class);

    private final ElementRepository elementRepository;
    private final WhiteboardService whiteboardService;
    private final UserService userService;

    public DrawingController(ElementRepository elementRepository,
                              WhiteboardService whiteboardService,
                              UserService userService) {
        this.elementRepository = elementRepository;
        this.whiteboardService = whiteboardService;
        this.userService = userService;
    }

    /**
     * Save canvas drawing as special element
     */
    @PostMapping("/save-canvas")
    public ResponseEntity<?> saveCanvas(
            @RequestParam Long boardId,
            @RequestParam String imageData,
            Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            requireWriteAccess(boardId, currentUser);

            Element canvasElement = elementRepository.findByBoardIdAndType(boardId, "canvas_image");

            if (canvasElement != null) {
                canvasElement.setData(imageData);
                canvasElement.setUpdatedAt(LocalDateTime.now());
                elementRepository.updateElement(canvasElement);
            } else {
                canvasElement = new Element();
                canvasElement.setBoardId(boardId);
                canvasElement.setCreatorId(currentUser.getUserId());
                canvasElement.setType("canvas_image");
                canvasElement.setZOrder(-1);  // Background layer
                canvasElement.setData(imageData);
                canvasElement.setCreatedAt(LocalDateTime.now());
                canvasElement.setUpdatedAt(LocalDateTime.now());
                elementRepository.save(canvasElement);
            }

            log.debug("Canvas saved for board {}", boardId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Canvas saved", "boardId", boardId));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception e) {
            log.error("Canvas save failed for board {}", boardId, e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to save canvas"));
        }
    }

    /**
     * Load canvas drawing
     */
    @GetMapping("/load-canvas/{boardId}")
    public ResponseEntity<?> loadCanvas(@PathVariable Long boardId, Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            requireReadAccess(boardId, currentUser);

            Element canvasElement = elementRepository.findByBoardIdAndType(boardId, "canvas_image");

            if (canvasElement != null && canvasElement.getData() != null) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "imageData", canvasElement.getData(),
                        "updatedAt", canvasElement.getUpdatedAt()));
            }

            return ResponseEntity.ok(Map.of("success", false, "message", "No canvas found"));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", ex.getMessage()));
        } catch (Exception e) {
            log.error("Canvas load failed for board {}", boardId, e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Failed to load canvas"));
        }
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User must be authenticated");
        }
        User currentUser = userService.findByUsername(authentication.getName());
        if (currentUser == null) {
            throw new AccessDeniedException("User not found");
        }
        return currentUser;
    }

    private void requireReadAccess(Long boardId, User currentUser) {
        Board board = whiteboardService.getWhiteboardById(boardId);
        if (board == null) {
            throw new AccessDeniedException("Board not found");
        }
        boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(currentUser.getUserId());
        String role = whiteboardService.getUserRoleInWhiteboard(currentUser.getUserId(), boardId);
        if (!isOwner && role == null) {
            throw new AccessDeniedException("You do not have access to this board");
        }
    }

    private void requireWriteAccess(Long boardId, User currentUser) {
        Board board = whiteboardService.getWhiteboardById(boardId);
        if (board == null) {
            throw new AccessDeniedException("Board not found");
        }
        boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(currentUser.getUserId());
        String role = whiteboardService.getUserRoleInWhiteboard(currentUser.getUserId(), boardId);
        boolean canWrite = isOwner || "editor".equalsIgnoreCase(role) || "owner".equalsIgnoreCase(role);
        if (!canWrite) {
            throw new AccessDeniedException("You do not have write access to this board");
        }
    }
}
