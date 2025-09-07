package com.example.collabodraw.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for whiteboard operations
 */
public class WhiteboardDto {
    
    @NotBlank(message = "Whiteboard name is required")
    @Size(min = 1, max = 100, message = "Whiteboard name must be between 1 and 100 characters")
    private String name;

    private Long ownerId;

    // Constructors
    public WhiteboardDto() {}

    public WhiteboardDto(String name, Long ownerId) {
        this.name = name;
        this.ownerId = ownerId;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
}
