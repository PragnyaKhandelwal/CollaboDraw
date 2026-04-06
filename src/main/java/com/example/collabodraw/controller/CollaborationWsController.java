package com.example.collabodraw.controller;

import com.example.collabodraw.model.dto.Participant;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.repository.CursorRepository;
import com.example.collabodraw.repository.SessionRepository;
import com.example.collabodraw.service.UserService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CollaborationWsController {
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionRepository sessionRepository;
    private final CursorRepository cursorRepository;
    private final UserService userService;
    private final com.example.collabodraw.service.RealtimeEventStore eventStore;
    private final Map<String, SessionBinding> wsSessionBindings = new ConcurrentHashMap<>();

    private static final class SessionBinding {
        private final Long boardId;
        private final Long dbSessionId;

        private SessionBinding(Long boardId, Long dbSessionId) {
            this.boardId = boardId;
            this.dbSessionId = dbSessionId;
        }
    }

    public CollaborationWsController(SimpMessagingTemplate messagingTemplate,
                                     SessionRepository sessionRepository,
                                     CursorRepository cursorRepository,
                                     UserService userService,
                                     com.example.collabodraw.service.RealtimeEventStore eventStore) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRepository = sessionRepository;
        this.cursorRepository = cursorRepository;
        this.userService = userService;
        this.eventStore = eventStore;
    }

    @MessageMapping("/board/{boardId}/join")
    public void join(@DestinationVariable Long boardId, Principal principal,
                     @Header("simpSessionId") String wsSessionId) {
        Long userId = resolveUserId(principal);
        if (userId == null) return;

        // Create one session row per websocket join so multi-tab presence is visible.
        Long createdSessionId = sessionRepository.create(boardId, userId);
        if (wsSessionId != null && createdSessionId != null) {
            SessionBinding previous = wsSessionBindings.put(wsSessionId, new SessionBinding(boardId, createdSessionId));
            if (previous != null && !previous.dbSessionId.equals(createdSessionId)) {
                sessionRepository.endBySessionId(previous.dbSessionId);
                if (!previous.boardId.equals(boardId)) {
                    broadcastParticipants(previous.boardId);
                }
            }
        }

        if (cursorRepository.findCursorId(boardId, userId) == null) {
            cursorRepository.insertCursor(boardId, userId, 0, 0);
        }

        broadcastParticipants(boardId);
    }

    @MessageMapping("/board/{boardId}/leave")
    public void leave(@DestinationVariable Long boardId, Principal principal,
                      @Header("simpSessionId") String wsSessionId) {
        Long userId = resolveUserId(principal);
        if (userId == null) return;

        SessionBinding binding = wsSessionId != null ? wsSessionBindings.remove(wsSessionId) : null;
        if (binding != null) {
            sessionRepository.endBySessionId(binding.dbSessionId);
            broadcastParticipants(binding.boardId);
            return;
        }

        // Fallback for legacy clients without simpSessionId.
        Long fallbackSessionId = sessionRepository.getActiveSessionId(boardId, userId);
        if (fallbackSessionId != null) {
            sessionRepository.end(fallbackSessionId, userId);
        }
        broadcastParticipants(boardId);
    }

    @MessageMapping("/board/{boardId}/heartbeat")
    public void heartbeat(@DestinationVariable Long boardId, Principal principal,
                          @Header("simpSessionId") String wsSessionId) {
        Long userId = resolveUserId(principal);
        if (userId == null) return;

        SessionBinding binding = wsSessionId != null ? wsSessionBindings.get(wsSessionId) : null;
        if (binding != null) {
            sessionRepository.heartbeatBySessionId(binding.dbSessionId);
        } else {
            // Fallback for legacy clients without simpSessionId.
            sessionRepository.heartbeatByBoard(boardId, userId);
        }

        // Self-heal occasional missed leave/disconnect events for all tabs.
        broadcastParticipants(boardId);
    }

    @EventListener
    public void onWebSocketDisconnect(SessionDisconnectEvent event) {
        String wsSessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (wsSessionId == null) return;

        SessionBinding binding = wsSessionBindings.remove(wsSessionId);
        if (binding == null) return;

        sessionRepository.endBySessionId(binding.dbSessionId);
        broadcastParticipants(binding.boardId);
    }

    public static class CursorMessage {
        public int x;
        public int y;
        public String clientId;
        public String displayName;
    }

    @MessageMapping("/board/{boardId}/cursor")
    public void cursor(@DestinationVariable Long boardId, @Payload CursorMessage msg, Principal principal,
                       @Header("simpSessionId") String sessionId) {
        Long userId = resolveUserId(principal);
        String displayName = resolveDisplayName(principal, sessionId, msg != null ? msg.displayName : null);
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
        event.put("username", displayName);
        event.put("displayName", displayName);
        event.put("clientId", msg.clientId);
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
        meta.put("by", principal != null ? principal.getName() : "");
        meta.put("userId", resolveUserId(principal));
        boolean isPartialStroke = msg != null && msg.payload != null && Boolean.TRUE.equals(msg.payload.get("partial"));
        meta.put("partial", isPartialStroke);
        envelope.put("meta", meta);
        // Store for late joiners
        if (!isPartialStroke) {
            eventStore.addEvent(boardId, envelope);
        }
        // Broadcast to subscribers
        messagingTemplate.convertAndSend("/topic/board." + boardId + ".elements", envelope);
    }

    private void broadcastParticipants(Long boardId) {
        List<Participant> participants = sessionRepository.activeParticipants(boardId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "participants");
        payload.put("items", participants);
        payload.put("connections", sessionRepository.activeConnectionCount(boardId));
        messagingTemplate.convertAndSend("/topic/board." + boardId + ".participants", payload);
    }

    private Long resolveUserId(Principal principal) {
        if (principal == null) return null;
        User user = userService.findByUsername(principal.getName());
        return user != null ? user.getUserId() : null;
    }

    private String resolveDisplayName(Principal principal, String sessionId, String requestedDisplayName) {
        if (principal != null) {
            User user = userService.findByUsername(principal.getName());
            if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
                return user.getUsername();
            }
            if (principal.getName() != null && !principal.getName().isBlank()) {
                return principal.getName();
            }
        }

        if (requestedDisplayName != null && !requestedDisplayName.isBlank()) {
            return requestedDisplayName;
        }

        return sessionId != null
                ? "Guest-" + sessionId.substring(0, Math.min(6, sessionId.length()))
                : "Guest";
    }
}
