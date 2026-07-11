package com.example.collabodraw.realtime;

import java.util.List;
import java.util.Map;

/**
 * Durable-ish log of live collaboration events (strokes, notes, moves) per board, used so
 * a late joiner can replay everything that happened since they weren't connected.
 *
 * The only implementation today ({@link InMemoryEventStore}) keeps this in a single JVM's
 * heap, which is fine for one server instance but means state doesn't survive a restart and
 * isn't visible to a second instance behind a load balancer. Depending on this interface
 * instead of the concrete class means a future Redis- or database-backed implementation is
 * a new @Service + a bean-selection property, not a rewrite of every caller - see
 * ARCHITECTURE.md for the full scaling story.
 */
public interface EventStore {

    void addEvent(Long boardId, Map<String, Object> event);

    List<Map<String, Object>> getEvents(Long boardId);
}
