package com.example.collabodraw.service;

import com.example.collabodraw.model.dto.WhiteboardDto;
import com.example.collabodraw.model.entity.Whiteboard;
import com.example.collabodraw.repository.WhiteboardRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for Whiteboard-related business logic
 */
@Service
public class WhiteboardService {
    
    private final WhiteboardRepository whiteboardRepository;

    public WhiteboardService(WhiteboardRepository whiteboardRepository) {
        this.whiteboardRepository = whiteboardRepository;
    }

    public Whiteboard createWhiteboard(WhiteboardDto whiteboardDto) {
        Whiteboard whiteboard = new Whiteboard(whiteboardDto.getName(), whiteboardDto.getOwnerId());
        int result = whiteboardRepository.save(whiteboard);
        if (result <= 0) {
            throw new RuntimeException("Failed to create whiteboard");
        }
        return whiteboard;
    }

    public List<Whiteboard> getWhiteboardsByOwner(Long ownerId) {
        return whiteboardRepository.findByOwnerId(ownerId);
    }

    public Whiteboard getWhiteboardById(Long id) {
        return whiteboardRepository.findById(id);
    }

    public List<Whiteboard> getAllWhiteboards() {
        return whiteboardRepository.findAll();
    }
}
