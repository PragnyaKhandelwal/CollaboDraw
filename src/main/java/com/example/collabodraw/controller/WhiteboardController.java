package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class WhiteboardController {

    private static final Logger log = LoggerFactory.getLogger(WhiteboardController.class);

    private final WhiteboardService whiteboardService;
    private final UserService userService;

    public WhiteboardController(WhiteboardService whiteboardService, UserService userService) {
        this.whiteboardService = whiteboardService;
        this.userService = userService;
    }

    // Handle /board/{boardId} route
    @GetMapping("/board/{boardId}")
    public String viewBoard(
            @PathVariable Long boardId,
            Authentication authentication,
            Model model) {

        try {
            Board board = whiteboardService.getWhiteboardById(boardId);
            if (board == null) {
                return "redirect:/home?error=notfound";
            }

            User currentUser = authentication != null ? userService.findByUsername(authentication.getName()) : null;
            if (currentUser == null) {
                return "redirect:/auth";
            }

            boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(currentUser.getUserId());
            String role = whiteboardService.getUserRoleInWhiteboard(currentUser.getUserId(), boardId);
            if (!isOwner && role == null) {
                return "redirect:/home?error=forbidden";
            }

            model.addAttribute("board", board);
            model.addAttribute("boardId", boardId);
            return "mainscreen";
        } catch (Exception e) {
            log.warn("Error loading board {}: {}", boardId, e.getMessage());
        }
        return "redirect:/home?error=notfound";
    }

    // Redirect legacy /whiteboard route to the single source of truth: /mainscreen
    @GetMapping("/whiteboard")
    public String whiteboard(
            @RequestParam(value = "board", required = false) String boardId,
            @RequestParam(value = "template", required = false) String templateId,
            @RequestParam(value = "shared", required = false) String sharedBoardId,
            @RequestParam(value = "preview", required = false) String previewId,
            @RequestParam(value = "duplicate", required = false) String duplicateId) {

        StringBuilder url = new StringBuilder("redirect:/mainscreen");
        String sep = "?";
        if (boardId != null) {
            url.append(sep).append("board=")
                    .append(URLEncoder.encode(boardId, StandardCharsets.UTF_8));
            sep = "&";
        }
        if (templateId != null) {
            url.append(sep).append("template=")
                    .append(URLEncoder.encode(templateId, StandardCharsets.UTF_8));
            sep = "&";
        }
        if (sharedBoardId != null) {
            url.append(sep).append("shared=")
                    .append(URLEncoder.encode(sharedBoardId, StandardCharsets.UTF_8));
            sep = "&";
        }
        if (previewId != null) {
            url.append(sep).append("preview=")
                    .append(URLEncoder.encode(previewId, StandardCharsets.UTF_8));
            sep = "&";
        }
        if (duplicateId != null) {
            url.append(sep).append("duplicate=")
                    .append(URLEncoder.encode(duplicateId, StandardCharsets.UTF_8));
        }

        return url.toString();
    }

    // Legacy route support
    @GetMapping("/whiteboard.html")
    public String whiteboardLegacy() {
        return "redirect:/mainscreen";
    }
}
