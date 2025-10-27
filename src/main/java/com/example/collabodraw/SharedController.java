package com.example.collabodraw.whiteboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SharedController {

    @GetMapping("/shared")
    public String shared(Model model) {
        // Add shared boards and collaboration data
        model.addAttribute("totalSharedBoards", 12);
        model.addAttribute("activeCollaborations", 8);
        model.addAttribute("pendingInvites", 3);
        model.addAttribute("recentActivityTime", "24h");
        
        // Mock current user data
        model.addAttribute("currentUser", new Object() {
            public String getInitials() { return "JD"; }
            public String getFullName() { return "John Doe"; }
        });
        
        // Mock shared stats
        model.addAttribute("sharedStats", new Object() {
            public int getTotalSharedBoards() { return 12; }
            public int getActiveCollaborations() { return 8; }
            public int getPendingInvites() { return 3; }
            public String getRecentActivityTime() { return "24h"; }
        });
        
        return "shared";
    }

    // Legacy route support
    @GetMapping("/shared.html")
    public String sharedLegacy() {
        return "redirect:/shared";
    }
}
