package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.repository.BoardMembershipRepository;
import com.example.collabodraw.repository.ActivityLogRepository;
import com.example.collabodraw.repository.BoardRepository;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class MyContentController {

    private final UserService userService;
    private final WhiteboardService whiteboardService;
    private final BoardMembershipRepository membershipRepository;
    private final ActivityLogRepository activityLogRepository;
    private final BoardRepository boardRepository;

    public MyContentController(UserService userService,
                               WhiteboardService whiteboardService,
                               BoardMembershipRepository membershipRepository,
                               ActivityLogRepository activityLogRepository,
                               BoardRepository boardRepository) {
        this.userService = userService;
        this.whiteboardService = whiteboardService;
        this.membershipRepository = membershipRepository;
        this.activityLogRepository = activityLogRepository;
        this.boardRepository = boardRepository;
    }

    @GetMapping("/my-content")
    public String myContent(Authentication authentication, Model model) {
        User currentUser = null;
        if (authentication != null && authentication.isAuthenticated()) {
            currentUser = userService.findByUsername(authentication.getName());
        }

        if (currentUser != null) {
            model.addAttribute("currentUser", currentUser);
            List<Board> myBoards = whiteboardService.getWhiteboardsByOwner(currentUser.getUserId());
            model.addAttribute("boards", myBoards);
            model.addAttribute("totalBoards", myBoards != null ? myBoards.size() : 0);

            var shared = membershipRepository.findByUserId(currentUser.getUserId());
            long sharedWithOthers = shared.stream().filter(m -> !"owner".equalsIgnoreCase(m.getRole())).count();
            model.addAttribute("sharedWithOthers", sharedWithOthers);

            // Metrics
            int templatesUsed = boardRepository.countByOwnerInDays(currentUser.getUserId(), 30); // proxy for usage
            int recentActivity = activityLogRepository.countRecentActivityForUserBoards(currentUser.getUserId(), 24);
            model.addAttribute("templatesUsed", templatesUsed);
            model.addAttribute("recentActivity", recentActivity);
        } else {
            // Not signed in: show public boards as a teaser
            List<Board> publicBoards = whiteboardService.getPublicWhiteboards();
            model.addAttribute("boards", publicBoards);
            model.addAttribute("totalBoards", publicBoards != null ? publicBoards.size() : 0);
        }

        return "my-content";
    }

    // Legacy route support
    @GetMapping("/my-content.html")
    public String myContentLegacy() {
        return "redirect:/my-content";
    }
}
