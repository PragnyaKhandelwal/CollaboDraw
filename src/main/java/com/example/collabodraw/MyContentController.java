package com.example.collabodraw.whiteboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyContentController {

    @GetMapping("/my-content")
    public String myContent(Model model) {
        // Add user's boards and statistics
        model.addAttribute("totalBoards", 24);
        model.addAttribute("sharedWithOthers", 8);
        model.addAttribute("templatesUsed", 12);
        model.addAttribute("recentActivity", 5);
        
        // Mock current user data
        model.addAttribute("currentUser", new Object() {
            public String getInitials() { return "JD"; }
            public String getFullName() { return "John Doe"; }
            public String getFirstName() { return "John"; }
            public String getLastName() { return "Doe"; }
            public String getEmail() { return "john.doe@example.com"; }
            public String getJobTitle() { return "UX Designer"; }
        });
        
        // Mock stats object
        model.addAttribute("stats", new Object() {
            public int getTotalBoards() { return 24; }
            public int getSharedWithOthers() { return 8; }
            public int getTemplatesUsed() { return 12; }
            public int getRecentActivity() { return 5; }
        });
        
        return "my-content";
    }

    // Legacy route support
    @GetMapping("/my-content.html")
    public String myContentLegacy() {
        return "redirect:/my-content";
    }
}
