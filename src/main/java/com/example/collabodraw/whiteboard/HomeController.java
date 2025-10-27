package com.example.collabodraw.whiteboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // Root mapping - redirects to home page
    @GetMapping("/")
    public String root() {
        return "redirect:/home";
    }

    // Home/Dashboard page
    @GetMapping("/home")
    public String home(Model model) {
        // Add sample data for dashboard
        model.addAttribute("totalBoards", 24);
        model.addAttribute("recentBoards", 5);
        model.addAttribute("sharedBoards", 8);
        model.addAttribute("templates", 12);
        
        // Mock recent boards data
        // In real app, fetch from service
        // model.addAttribute("recentBoardsList", boardService.getRecentBoards());
        
        return "home";
    }

    // Dashboard redirect (alternative)
    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/home";
    }

    // Legacy route support
    @GetMapping("/home.html")
    public String homeLegacy() {
        return "redirect:/home";
    }
}
