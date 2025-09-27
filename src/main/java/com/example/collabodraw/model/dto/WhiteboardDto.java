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
    private Boolean isPublic = false;

    // Constructors
    public WhiteboardDto() {}

    public WhiteboardDto(String name, Long ownerId) {
        this.name = name;
        this.ownerId = ownerId;
        this.isPublic = false;
    }

    public WhiteboardDto(String name, Long ownerId, Boolean isPublic) {
        this.name = name;
        this.ownerId = ownerId;
        this.isPublic = isPublic;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
}
