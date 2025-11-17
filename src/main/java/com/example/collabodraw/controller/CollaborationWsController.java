package com.example.collabodraw.controller;

import com.example.collabodraw.model.dto.Participant;
import com.example.collabodraw.repository.CursorRepository;
import com.example.collabodraw.repository.SessionRepository;
import com.example.collabodraw.repository.UserRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
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

    public CollaborationWsController(SimpMessagingTemplate messagingTemplate,
                                     SessionRepository sessionRepository,
                                     CursorRepository cursorRepository,
                                     UserRepository userRepository) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRepository = sessionRepository;
        this.cursorRepository = cursorRepository;
        this.userRepository = userRepository;
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
    public void cursor(@DestinationVariable Long boardId, @Payload CursorMessage msg, Principal principal) {
        Long userId = resolveUserId(principal);
        if (userId == null) return;

        Long cursorId = cursorRepository.findCursorId(boardId, userId);
        if (cursorId == null) {
            cursorId = cursorRepository.insertCursor(boardId, userId, msg.x, msg.y);
        } else {
            cursorRepository.updateCursor(cursorId, msg.x, msg.y);
        }

        Map<String, Object> event = new HashMap<>();
        event.put("type", "cursor");
        event.put("userId", userId);
        event.put("username", principal != null ? principal.getName() : "");
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
