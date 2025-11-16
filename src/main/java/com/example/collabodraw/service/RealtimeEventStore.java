package com.example.collabodraw.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory store for live collaboration element events.
 * Provides simple persistence across user joins during a single JVM run.
 * NOTE: This is volatile; consider replacing with database persistence for durability.
 */
@Service
public class RealtimeEventStore {

    private static final int MAX_EVENTS_PER_BOARD = 5000; // safeguard
    private final Map<Long, List<Map<String, Object>>> boardEvents = new ConcurrentHashMap<>();

    public void addEvent(Long boardId, Map<String, Object> event) {
        if (boardId == null || event == null) return;
        List<Map<String, Object>> list = boardEvents.computeIfAbsent(boardId, k -> new CopyOnWriteArrayList<>());
        list.add(event);
        // Trim if oversized
        if (list.size() > MAX_EVENTS_PER_BOARD) {
            int removeCount = list.size() - MAX_EVENTS_PER_BOARD;
            for (int i = 0; i < removeCount; i++) {
                list.remove(0);
            }
        }
    }

    public List<Map<String, Object>> getEvents(Long boardId) {
        if (boardId == null) return Collections.emptyList();
        return boardEvents.getOrDefault(boardId, Collections.emptyList());
    }
}
