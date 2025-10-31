package com.example.collabodraw;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class WhiteboardController {

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
        if (boardId != null) { url.append(sep).append("board=")
                .append(URLEncoder.encode(boardId, StandardCharsets.UTF_8)); sep = "&"; }
        if (templateId != null) { url.append(sep).append("template=")
                .append(URLEncoder.encode(templateId, StandardCharsets.UTF_8)); sep = "&"; }
        if (sharedBoardId != null) { url.append(sep).append("shared=")
                .append(URLEncoder.encode(sharedBoardId, StandardCharsets.UTF_8)); sep = "&"; }
        if (previewId != null) { url.append(sep).append("preview=")
                .append(URLEncoder.encode(previewId, StandardCharsets.UTF_8)); sep = "&"; }
        if (duplicateId != null) { url.append(sep).append("duplicate=")
                .append(URLEncoder.encode(duplicateId, StandardCharsets.UTF_8)); }

        return url.toString();
    }

    // Legacy route support
    @GetMapping("/whiteboard.html")
    public String whiteboardLegacy() {
        return "redirect:/mainscreen";
    }
}
