package com.example.collabodraw.service;

import com.example.collabodraw.model.dto.DrawingElementDTO;
import com.example.collabodraw.model.entity.Element;
import com.example.collabodraw.repository.ElementRepository;
import com.example.collabodraw.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DrawingService {

    @Autowired
    private ElementRepository elementRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Save drawing elements for a board (replaces existing)
     */
    @Transactional
    public void saveDrawing(Long boardId, List<DrawingElementDTO> elements, String username) {
        Long userId = userRepository.findByUsername(username).getUserId();
        
        // Clear existing elements for this board
        elementRepository.deleteByBoardId(boardId);
        
        // Save new elements
        for (DrawingElementDTO dto : elements) {
            Element element = new Element();
            element.setBoardId(boardId);
            element.setCreatorId(userId);
            element.setType(dto.getType());
            element.setData(dto.getData());
            element.setZOrder(dto.getzOrder());
            element.setCreatedAt(LocalDateTime.now());
            element.setUpdatedAt(LocalDateTime.now());
            
            // Save returns the generated ID
            Long elementId = elementRepository.save(element);
            element.setElementId(elementId);
        }
    }

    /**
     * Load drawing elements for a board
     */
    public List<DrawingElementDTO> loadDrawing(Long boardId) {
        List<Element> elements = elementRepository.findByBoardId(boardId);
        
        return elements.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Clear all drawing elements from a board
     */
    @Transactional
    public void saveDrawing(Long boardId, List<DrawingElementDTO> elements, String username) {
        Long userId = userRepository.findByUsername(username).getUserId();
        
        System.out.println("🔵 [DrawingService] Saving " + elements.size() + " elements for board " + boardId);
        
        // Clear existing
        elementRepository.deleteByBoardId(boardId);
        System.out.println("🔵 [DrawingService] Cleared old elements");
        
        // Save new
        for (DrawingElementDTO dto : elements) {
            Element element = new Element();
            element.setBoardId(boardId);
            element.setCreatorId(userId);
            element.setType(dto.getType());
            element.setData(dto.getData());
            element.setZOrder(dto.getzOrder());
            element.setCreatedAt(LocalDateTime.now());
            element.setUpdatedAt(LocalDateTime.now());
            
            Long elementId = elementRepository.save(element);
            System.out.println("🟢 [DrawingService] Saved element ID: " + elementId + " type: " + dto.getType());
        }
    }
    
    /**
     * Get drawing statistics for a board
     */
    public int getElementCount(Long boardId) {
        return elementRepository.countByBoardId(boardId);
    }

    /**
     * Convert Element entity to DTO
     */
    private DrawingElementDTO convertToDTO(Element element) {
        DrawingElementDTO dto = new DrawingElementDTO();
        dto.setElementId(element.getElementId());
        dto.setType(element.getType());
        dto.setData(element.getData());
        dto.setzOrder(element.getZOrder());
        return dto;
    }
}
