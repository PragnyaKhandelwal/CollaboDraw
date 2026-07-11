package com.example.collabodraw.service;

import com.example.collabodraw.model.entity.Template;
import com.example.collabodraw.repository.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class TemplateService {
    private final TemplateRepository templateRepository;
    // Fallback only: used when the DB usage-count update fails. Previously backed by
    // java.util.prefs.Preferences, which on Windows is the registry and on Linux is a
    // per-user file store that may be unwritable/non-existent in a container - it would
    // silently no-op or diverge per instance. An in-memory map has the same "not shared
    // across instances" limitation as any per-process fallback, but at least behaves
    // identically on every OS/deployment target instead of failing unpredictably.
    private final Map<String, AtomicInteger> fallbackUsage = new ConcurrentHashMap<>();

    public TemplateService(TemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public List<Template> getAllTemplates() {
        List<Template> fromDb = templateRepository.findAll();
        if (fromDb == null || fromDb.isEmpty()) {
            return defaultTemplates();
        }
        fromDb.forEach(this::applyIconFallback);
        return fromDb;
    }

    public List<Template> getPopularTemplates(int limit) {
        List<Template> all = getAllTemplates();
        int cap = Math.max(limit, 0);
        return all.stream()
                .filter(t -> t.isFeatured() || "popular".equalsIgnoreCase(t.getCategory()) || t.getUsageCount() > 0)
                .limit(cap)
                .collect(Collectors.toList());
    }

    public Template getTemplateByKey(String key) {
        if (key == null || key.isBlank()) return null;
        Template dbTemplate = templateRepository.findByKey(key);
        if (dbTemplate != null) {
            applyIconFallback(dbTemplate);
            return dbTemplate;
        }
        return defaultTemplates().stream()
                .filter(t -> key.equalsIgnoreCase(t.getTemplateKey()))
                .findFirst()
                .orElse(null);
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
        boolean incrementedDb = false;
        try {
            incrementedDb = templateRepository.incrementUsage(templateKey) > 0;
        } catch (Exception ignored) {
            incrementedDb = false;
        }
        if (!incrementedDb) {
            incrementFallbackUsage(templateKey);
        }
    }

    private List<Template> defaultTemplates() {
        List<Template> items = new ArrayList<>();
        items.add(template("mindmap", "Mind Map", "Organize ideas and branches quickly", "popular", "zap", "FREE", false, true, 128));
        items.add(template("kanban", "Kanban Board", "Track backlog, in-progress, and done", "popular", "grid", "FREE", false, true, 114));
        items.add(template("flowchart", "Flowchart", "Visualize processes and decisions", "business", "shuffle", "FREE", false, true, 96));
        items.add(template("wireframe", "Wireframe", "Design web and app layouts", "design", "edit", "FREE", false, true, 88));
        items.add(template("swot", "SWOT Analysis", "Map strengths, weaknesses, opportunities, threats", "business", "target", "FREE", false, false, 77));
        items.add(template("retrospective", "Sprint Retrospective", "Capture what went well and action items", "planning", "lightbulb", "FREE", true, false, 68));
        items.add(template("roadmap", "Product Roadmap", "Plan timeline and milestones", "planning", "bar-chart", "PRO", false, true, 63));
        items.add(template("customer-journey", "Customer Journey", "Map user touchpoints and pain points", "design", "users", "PRO", false, false, 55));
        items.add(template("lecture-notes", "Lecture Notes", "Collaborative lecture and class notes", "education", "file-text", "FREE", false, false, 49));
        items.add(template("research-board", "Research Board", "Collect findings and references", "education", "search", "FREE", true, false, 42));
        items.add(template("okrs", "OKR Planner", "Set objectives and key results", "business", "target", "PRO", false, false, 37));
        items.add(template("blank", "Blank Board", "Start from an empty canvas", "popular", "file-text", "FREE", false, false, 999));
        for (Template item : items) {
            int fallback = getFallbackUsage(item.getTemplateKey());
            if (fallback > 0) {
                item.setUsageCount(Math.max(item.getUsageCount(), fallback));
            }
        }
        return items;
    }

    // Templates loaded from the DB predate the icon column being used - every row today has
    // icon = NULL, which made every card in the gallery render the exact same placeholder.
    // Rather than requiring a manual DB write per template, pick a sensible icon from the
    // template's key (specific templates we know about) or its category (generic fallback),
    // so a newly-added template row gets a reasonable icon for free. Values are icon-sprite
    // symbol names (see fragments/icons.html), not emoji - callers render
    // <use th:attr="href='#icon-' + ${t.icon}">, not text.
    private static final Map<String, String> ICON_BY_TEMPLATE_KEY = Map.of(
            "team_brainstorm", "zap",
            "product_roadmap", "bar-chart",
            "kanban_board", "grid"
    );
    private static final Map<String, String> ICON_BY_CATEGORY = Map.of(
            "popular", "zap",
            "business", "bar-chart",
            "design", "palette",
            "education", "file-text",
            "planning", "target"
    );
    private static final String DEFAULT_ICON = "file-text";

    private void applyIconFallback(Template t) {
        if (t == null || (t.getIcon() != null && !t.getIcon().isBlank())) return;
        String byKey = t.getTemplateKey() != null ? ICON_BY_TEMPLATE_KEY.get(t.getTemplateKey().toLowerCase()) : null;
        if (byKey != null) {
            t.setIcon(byKey);
            return;
        }
        String byCategory = t.getCategory() != null ? ICON_BY_CATEGORY.get(t.getCategory().toLowerCase()) : null;
        t.setIcon(byCategory != null ? byCategory : DEFAULT_ICON);
    }

    private Template template(String key, String name, String description, String category,
                              String icon, String plan, boolean isNew, boolean featured, int usage) {
        Template t = new Template();
        t.setTemplateKey(key);
        t.setName(name);
        t.setDescription(description);
        t.setCategory(category);
        t.setIcon(icon);
        t.setPlan(plan);
        t.setNew(isNew);
        t.setFeatured(featured);
        t.setUsageCount(usage);
        return t;
    }

    private int getFallbackUsage(String key) {
        if (key == null || key.isBlank()) return 0;
        AtomicInteger count = fallbackUsage.get(key);
        return count != null ? count.get() : 0;
    }

    private void incrementFallbackUsage(String key) {
        if (key == null || key.isBlank()) return;
        fallbackUsage.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }
}
