package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.repository.BoardMembershipRepository;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.repository.ActivityLogRepository;
import com.example.collabodraw.service.WhiteboardService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class SharedController {

    private final UserService userService;
    private final WhiteboardService whiteboardService;
    private final BoardMembershipRepository membershipRepository;
    private final ActivityLogRepository activityLogRepository;

    public SharedController(UserService userService,
                            WhiteboardService whiteboardService,
                            BoardMembershipRepository membershipRepository,
                            ActivityLogRepository activityLogRepository) {
        this.userService = userService;
        this.whiteboardService = whiteboardService;
        this.membershipRepository = membershipRepository;
        this.activityLogRepository = activityLogRepository;
    }

    @GetMapping("/shared")
    public String shared(Authentication authentication, Model model) {
        User currentUser = null;
        if (authentication != null && authentication.isAuthenticated()) {
            currentUser = userService.findByUsername(authentication.getName());
        }

        if (currentUser != null) {
            model.addAttribute("currentUser", currentUser);
            var memberships = membershipRepository.findByUserId(currentUser.getUserId());
            long sharedBoards = memberships.stream().filter(m -> !"owner".equalsIgnoreCase(m.getRole())).count();
            model.addAttribute("totalSharedBoards", sharedBoards);
            model.addAttribute("activeCollaborations", memberships.size());
            model.addAttribute("pendingInvites", 0); // No invites table yet
            int recentActivity = activityLogRepository.countRecentActivityForUserBoards(currentUser.getUserId(), 24);
            model.addAttribute("recentActivity", recentActivity);
            model.addAttribute("recentActivityTime", "24h");

            // Optional: show public boards too
            List<Board> publicBoards = whiteboardService.getPublicWhiteboards();
            model.addAttribute("publicBoards", publicBoards);
        } else {
            // Not logged in, show public info only
            List<Board> publicBoards = whiteboardService.getPublicWhiteboards();
            model.addAttribute("publicBoards", publicBoards);
            model.addAttribute("totalSharedBoards", publicBoards != null ? publicBoards.size() : 0);
            model.addAttribute("activeCollaborations", 0);
            model.addAttribute("pendingInvites", 0);
            model.addAttribute("recentActivity", 0);
            model.addAttribute("recentActivityTime", "24h");
        }

        return "shared";
    }

    // Legacy route support
    @GetMapping("/shared.html")
    public String sharedLegacy() {
        return "redirect:/shared";
    }
}
