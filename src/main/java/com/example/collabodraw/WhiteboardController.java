package com.example.collabodraw.whiteboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WhiteboardController {

    // Main whiteboard/canvas page
    @GetMapping({"/whiteboard", "/mainscreen"})
    public String whiteboard(
            @RequestParam(value = "board", required = false) String boardId,
            @RequestParam(value = "template", required = false) String templateId,
            @RequestParam(value = "shared", required = false) String sharedBoardId,
            @RequestParam(value = "copy-shared", required = false) String copySharedId,
            @RequestParam(value = "duplicate", required = false) String duplicateId,
            Model model) {
        
        // Handle different whiteboard initialization scenarios
        if (boardId != null) {
            model.addAttribute("boardId", boardId);
            model.addAttribute("mode", "edit");
        } else if (templateId != null) {
            model.addAttribute("templateId", templateId);
            model.addAttribute("mode", "template");
        } else if (sharedBoardId != null) {
            model.addAttribute("sharedBoardId", sharedBoardId);
            model.addAttribute("mode", "shared");
        } else if (copySharedId != null) {
            model.addAttribute("copySharedId", copySharedId);
            model.addAttribute("mode", "copy-shared");
        } else if (duplicateId != null) {
            model.addAttribute("duplicateId", duplicateId);
            model.addAttribute("mode", "duplicate");
        } else {
            model.addAttribute("mode", "new");
        }
        
        return "mainscreen";
    }

    // Legacy route support
    @GetMapping("/whiteboard.html")
    public String whiteboardLegacy() {
        return "redirect:/mainscreen";
    }
}
