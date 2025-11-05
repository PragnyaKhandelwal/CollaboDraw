package com.example.collabodraw.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for drawing elements
 * Used to transfer drawing data between frontend and backend
 */
public class DrawingElementDTO {
    
    @JsonProperty("elementId")
    private Long elementId;
    
    @JsonProperty("type")
    private String type;  // 'line', 'rectangle', 'circle', 'text', 'image', etc.
    
    @JsonProperty("data")
    private String data;  // JSON string containing element-specific data
    
    @JsonProperty("zOrder")
    private Integer zOrder;  // Layer order for rendering
    
    // Constructors
    public DrawingElementDTO() {
    }
    
    public DrawingElementDTO(String type, String data, Integer zOrder) {
        this.type = type;
        this.data = data;
        this.zOrder = zOrder;
    }
    
    // Getters and Setters
    public Long getElementId() {
        return elementId;
    }
    
    public void setElementId(Long elementId) {
        this.elementId = elementId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getData() {
        return data;
    }
    
    public void setData(String data) {
        this.data = data;
    }
    
    public Integer getzOrder() {
        return zOrder;
    }
    
    public void setzOrder(Integer zOrder) {
        this.zOrder = zOrder;
    }
    
    @Override
    public String toString() {
        return "DrawingElementDTO{" +
                "elementId=" + elementId +
                ", type='" + type + '\'' +
                ", data='" + data + '\'' +
                ", zOrder=" + zOrder +
                '}';
    }
}
