package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.Element;
import com.example.collabodraw.repository.ElementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/drawings")
@CrossOrigin(origins = "http://localhost:8080", allowCredentials = "true")  // ✅ FIXED
public class DrawingController {
    
    @Autowired
    private ElementRepository elementRepository;
    
    /**
     * Save canvas drawing as special element
     */
    @PostMapping("/save-canvas")
    public ResponseEntity<?> saveCanvas(
            @RequestParam Long boardId,
            @RequestParam String imageData) {
        
        System.out.println("💾 API: Saving canvas for board " + boardId);
        
        try {
            // Find existing canvas element
            Element canvasElement = elementRepository.findByBoardIdAndType(boardId, "canvas_image");
            
            if (canvasElement != null) {
                // Update existing
                canvasElement.setData(imageData);
                canvasElement.setUpdatedAt(LocalDateTime.now());
                elementRepository.updateElement(canvasElement);
                System.out.println("✅ API: Canvas updated for board " + boardId);
            } else {
                // Create new
                canvasElement = new Element();
                canvasElement.setBoardId(boardId);
                canvasElement.setCreatorId(1L);  // System user
                canvasElement.setType("canvas_image");
                canvasElement.setZOrder(-1);  // Background layer
                canvasElement.setData(imageData);
                canvasElement.setCreatedAt(LocalDateTime.now());
                canvasElement.setUpdatedAt(LocalDateTime.now());
                elementRepository.save(canvasElement);
                System.out.println("✅ API: Canvas created for board " + boardId);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Canvas saved");
            response.put("boardId", boardId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("❌ API Save error: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * Load canvas drawing
     */
    @GetMapping("/load-canvas/{boardId}")
    public ResponseEntity<?> loadCanvas(@PathVariable Long boardId) {
        System.out.println("📥 API: Loading canvas for board " + boardId);
        
        try {
            Element canvasElement = elementRepository.findByBoardIdAndType(boardId, "canvas_image");
            
            if (canvasElement != null && canvasElement.getData() != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("imageData", canvasElement.getData());
                response.put("updatedAt", canvasElement.getUpdatedAt());
                
                System.out.println("✅ API: Canvas loaded for board " + boardId);
                return ResponseEntity.ok(response);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "No canvas found");
            
            System.out.println("ℹ️ API: No canvas found for board " + boardId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("❌ API Load error: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}
