package com.example.collabodraw.model.entity;

/**
 * Template entity representing a whiteboard template definition
 * Maps to 'templates' table
 */
public class Template {
    private Long templateId;
    private String templateKey; // e.g., blank, mindmap, kanban
    private String name;
    private String description;
    private String category; // popular, business, design, education, planning, etc.
    private String icon; // emoji or icon hint
    private String plan; // FREE or PRO
    private boolean isNew;
    private boolean isFeatured;
    private int usageCount;

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getTemplateKey() { return templateKey; }
    public void setTemplateKey(String templateKey) { this.templateKey = templateKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public boolean isNew() { return isNew; }
    public void setNew(boolean aNew) { isNew = aNew; }

    public boolean isFeatured() { return isFeatured; }
    public void setFeatured(boolean featured) { isFeatured = featured; }

    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }
}
