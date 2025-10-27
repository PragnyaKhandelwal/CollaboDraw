package com.example.collabodraw.whiteboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TemplatesController {

    @GetMapping("/templates")
    public String templates(Model model) {
        // Add available templates and categories
        model.addAttribute("totalTemplates", 15);
        model.addAttribute("popularTemplates", 8);
        model.addAttribute("businessTemplates", 5);
        model.addAttribute("designTemplates", 7);
        
        return "templates";
    }

    // Legacy route support
    @GetMapping("/templates.html")
    public String templatesLegacy() {
        return "redirect:/templates";
    }
}
