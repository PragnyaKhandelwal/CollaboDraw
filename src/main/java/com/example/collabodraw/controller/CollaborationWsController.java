package com.example.collabodraw.controller;

import com.example.collabodraw.model.dto.Participant;
import com.example.collabodraw.repository.CursorRepository;
import com.example.collabodraw.repository.SessionRepository;
import com.example.collabodraw.repository.UserRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CollaborationWsController {
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionRepository sessionRepository;
    private final CursorRepository cursorRepository;
    private final UserRepository userRepository;
    private final com.example.collabodraw.service.RealtimeEventStore eventStore;

    public CollaborationWsController(SimpMessagingTemplate messagingTemplate,
                                     SessionRepository sessionRepository,
                                     CursorRepository cursorRepository,
                                     UserRepository userRepository,
                                     com.example.collabodraw.service.RealtimeEventStore eventStore) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRepository = sessionRepository;
        this.cursorRepository = cursorRepository;
        this.userRepository = userRepository;
        this.eventStore = eventStore;
    }

    @MessageMapping("/board/{boardId}/join")
    public void join(@DestinationVariable Long boardId, Principal principal) {
        Long userId = resolveUserId(principal);
        if (userId == null) return;

        if (!sessionRepository.hasActive(boardId, userId)) {
            sessionRepository.create(boardId, userId);
        }
        if (cursorRepository.findCursorId(boardId, userId) == null) {
            cursorRepository.insertCursor(boardId, userId, 0, 0);
        }

        broadcastParticipants(boardId);
    }

    @MessageMapping("/board/{boardId}/leave")
    public void leave(@DestinationVariable Long boardId, Principal principal) {
        Long userId = resolveUserId(principal);
        if (userId == null) return;
        Long sid = sessionRepository.getActiveSessionId(boardId, userId);
        if (sid != null) {
            sessionRepository.end(sid, userId);
        }
        broadcastParticipants(boardId);
    }

    @MessageMapping("/board/{boardId}/heartbeat")
    public void heartbeat(@DestinationVariable Long boardId, Principal principal) {
        Long userId = resolveUserId(principal);
        if (userId == null) return;
        Long sid = sessionRepository.getActiveSessionId(boardId, userId);
        if (sid != null) {
            sessionRepository.heartbeat(sid, userId);
        }
        // Optionally broadcast presence; keep light and only broadcast on join/leave
    }

    public static class CursorMessage {
        public int x;
        public int y;
    }

    @MessageMapping("/board/{boardId}/cursor")
    public void cursor(@DestinationVariable Long boardId, @Payload CursorMessage msg, Principal principal,
                       @Header("simpSessionId") String sessionId) {
        Long userId = resolveUserId(principal);
        // Update persistent cursor position only for authenticated users
        if (userId != null) {
            Long cursorId = cursorRepository.findCursorId(boardId, userId);
            if (cursorId == null) {
                cursorId = cursorRepository.insertCursor(boardId, userId, msg.x, msg.y);
            } else {
                cursorRepository.updateCursor(cursorId, msg.x, msg.y);
            }
        }

        Map<String, Object> event = new HashMap<>();
        event.put("type", "cursor");
        event.put("userId", userId);
        // Provide a stable guest name when unauthenticated so clients can show active users
        String uname = (principal != null && principal.getName() != null && !principal.getName().isBlank())
                ? principal.getName()
                : (sessionId != null ? ("Guest-" + sessionId.substring(0, Math.min(6, sessionId.length()))) : "Guest");
        event.put("username", uname);
        event.put("x", msg.x);
        event.put("y", msg.y);
        event.put("timestamp", LocalDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/board." + boardId + ".cursors", event);
    }

    public static class VersionMessage {
        public String id;
        public String description;
        public String timestamp;
    }

    public static class ElementMessage {
        public String kind; // e.g. stroke, sticky, text
        public Map<String, Object> payload; // arbitrary element data
    }

    @MessageMapping("/board/{boardId}/version")
    public void version(@DestinationVariable Long boardId, @Payload VersionMessage msg, Principal principal) {
        // Broadcast minimal version event; persistence is handled via REST already
        Map<String, Object> event = new HashMap<>();
        event.put("type", "version");
        event.put("id", msg != null ? msg.id : null);
        event.put("description", msg != null ? msg.description : "");
        event.put("timestamp", msg != null ? msg.timestamp : "");
        event.put("by", principal != null ? principal.getName() : "");
        messagingTemplate.convertAndSend("/topic/board." + boardId + ".versions", event);
    }

    @MessageMapping("/board/{boardId}/element")
    public void element(@DestinationVariable Long boardId, @Payload ElementMessage msg, Principal principal) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("type", "element");
        envelope.put("by", principal != null ? principal.getName() : "");
        envelope.put("timestamp", LocalDateTime.now().toString());
        envelope.put("payload", msg != null ? msg.payload : null);
        Map<String, Object> meta = new HashMap<>();
        meta.put("kind", msg != null ? msg.kind : null);
        envelope.put("meta", meta);
        // Store for late joiners
        eventStore.addEvent(boardId, envelope);
        // Broadcast to subscribers
        messagingTemplate.convertAndSend("/topic/board." + boardId + ".elements", envelope);
    }

    private void broadcastParticipants(Long boardId) {
        List<Participant> participants = sessionRepository.activeParticipants(boardId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "participants");
        payload.put("items", participants);
        messagingTemplate.convertAndSend("/topic/board." + boardId + ".participants", payload);
    }

    private Long resolveUserId(Principal principal) {
        if (principal == null) return null;
        var user = userRepository.findByUsername(principal.getName());
        return user != null ? user.getUserId() : null;
    }
}
