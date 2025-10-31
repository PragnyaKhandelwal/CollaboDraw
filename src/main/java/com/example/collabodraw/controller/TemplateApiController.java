package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.Template;
import com.example.collabodraw.service.TemplateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
public class TemplateApiController {

    private final TemplateService templateService;

    public TemplateApiController(TemplateService templateService) {
        this.templateService = templateService;
    }

    // Back-compat simple endpoint
    @GetMapping("/content/{templateId}")
    public ResponseEntity<Map<String, Object>> getTemplateContent(@PathVariable String templateId) {
        Template t = templateService.getTemplateByKey(templateId);
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("id", templateId);
        templateData.put("title", t != null ? t.getName() : ("Template " + templateId));
        templateData.put("description", t != null ? t.getDescription() : "Template description");
        return ResponseEntity.ok(templateData);
    }

    // Used by frontend to create a new board from a template
    @GetMapping("/use/{templateId}")
    public ResponseEntity<Map<String, Object>> useTemplate(@PathVariable String templateId) {
        Map<String, Object> body = generateTemplatePayload(templateId, false);
        if (!(Boolean) body.getOrDefault("success", false)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        return ResponseEntity.ok(body);
    }

    // Used by frontend to preview a template in read-only mode
    @GetMapping("/preview/{templateId}")
    public ResponseEntity<Map<String, Object>> previewTemplate(@PathVariable String templateId) {
        Map<String, Object> body = generateTemplatePayload(templateId, true);
        if (!(Boolean) body.getOrDefault("success", false)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> generateTemplatePayload(String templateKey, boolean readOnly) {
        Map<String, Object> payload = new HashMap<>();
        Template t = templateService.getTemplateByKey(templateKey);
        if (t == null) {
            // Allow "blank" even if DB not populated yet
            if (!"blank".equalsIgnoreCase(templateKey)) {
                payload.put("success", false);
                payload.put("message", "Template not found");
                return payload;
            }
        }

        String title = (t != null ? t.getName() : "Blank");
        String elements = buildTemplateElementsHtml(templateKey);
        Map<String, Object> settings = new HashMap<>();
        settings.put("zoom", 1);
        settings.put("pan", Map.of("x", 0, "y", 0));
        settings.put("tool", "select");
        settings.put("color", "#000000");

        payload.put("success", true);
        payload.put("id", templateKey);
        payload.put("title", title);
        payload.put("elements", elements);
        payload.put("settings", settings);
        if (readOnly) payload.put("readOnly", true);
        return payload;
    }

    private String buildTemplateElementsHtml(String key) {
        String k = key == null ? "" : key.toLowerCase();
        switch (k) {
            case "kanban":
                return "" +
                        "<div class=\"canvas-element\" style=\"left:40px;top:40px;width:280px;height:380px;background:#f3f4f6;border-radius:8px;padding:10px;\">" +
                        "<div style=\"font-weight:600;margin-bottom:8px;\">To Do</div>" +
                        "<div class=\"sticky-note\" style=\"margin:6px 0;\"><input class=\"sticky-title\" value=\"Task A\"></div>" +
                        "<div class=\"sticky-note\" style=\"margin:6px 0;\"><input class=\"sticky-title\" value=\"Task B\"></div>" +
                        "</div>" +
                        "<div class=\"canvas-element\" style=\"left:360px;top:40px;width:280px;height:380px;background:#f3f4f6;border-radius:8px;padding:10px;\">" +
                        "<div style=\"font-weight:600;margin-bottom:8px;\">In Progress</div>" +
                        "</div>" +
                        "<div class=\"canvas-element\" style=\"left:680px;top:40px;width:280px;height:380px;background:#f3f4f6;border-radius:8px;padding:10px;\">" +
                        "<div style=\"font-weight:600;margin-bottom:8px;\">Done</div>" +
                        "</div>";
            case "mindmap":
                return "" +
                        "<div class=\"canvas-element\" style=\"left:200px;top:150px;padding:12px 16px;border:2px solid #3b82f6;border-radius:8px;\">Central Idea</div>" +
                        "<div class=\"canvas-element\" style=\"left:60px;top:80px;padding:8px 12px;border:1px solid #9ca3af;border-radius:6px;\">Branch 1</div>" +
                        "<div class=\"canvas-element\" style=\"left:380px;top:80px;padding:8px 12px;border:1px solid #9ca3af;border-radius:6px;\">Branch 2</div>";
            case "flowchart":
                return "" +
                        "<div class=\"canvas-element\" style=\"left:120px;top:60px;padding:10px 14px;border:2px solid #10b981;border-radius:8px;\">Start</div>" +
                        "<div class=\"canvas-element\" style=\"left:120px;top:160px;padding:10px 14px;border:2px solid #f59e0b;border-radius:8px;\">Process</div>" +
                        "<div class=\"canvas-element\" style=\"left:120px;top:260px;padding:10px 14px;border:2px solid #ef4444;border-radius:8px;\">End</div>";
            case "blank":
            default:
                return "";
        }
    }
}
