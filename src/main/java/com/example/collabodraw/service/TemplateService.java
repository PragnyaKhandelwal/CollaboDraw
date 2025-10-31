package com.example.collabodraw.service;

import com.example.collabodraw.model.entity.Template;
import com.example.collabodraw.repository.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemplateService {
    private final TemplateRepository templateRepository;

    public TemplateService(TemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public List<Template> getAllTemplates() {
        return templateRepository.findAll();
    }

    public Template getTemplateByKey(String key) {
        if (key == null || key.isBlank()) return null;
        return templateRepository.findByKey(key);
    }

    public Map<String, Integer> getCategoryCounts() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("all", templateRepository.countAll());
        // Common categories (extendable)
        counts.put("popular", templateRepository.countByCategory("popular"));
        counts.put("business", templateRepository.countByCategory("business"));
        counts.put("design", templateRepository.countByCategory("design"));
        counts.put("education", templateRepository.countByCategory("education"));
        counts.put("planning", templateRepository.countByCategory("planning"));
        return counts;
    }

    public void incrementUsage(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) return;
        templateRepository.incrementUsage(templateKey);
    }
}
