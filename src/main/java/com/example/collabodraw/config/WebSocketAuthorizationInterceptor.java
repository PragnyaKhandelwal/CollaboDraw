package com.example.collabodraw.config;

import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blocks SUBSCRIBE frames to board-scoped topics from callers who aren't a member/owner of
 * that board. This exists because the per-handler checks in CollaborationWsController only
 * gate the @MessageMapping SEND destinations (/app/board/{id}/...) - Spring's simple broker
 * routes SUBSCRIBE frames straight to itself with no per-destination authorization by default,
 * so an authenticated client could otherwise SUBSCRIBE directly to
 * /topic/board.{anyId}.elements (or .cursors/.participants/.presence/.versions) and silently
 * read another board's live activity without ever calling the join handler.
 */
@Component
public class WebSocketAuthorizationInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthorizationInterceptor.class);
    private static final Pattern BOARD_TOPIC = Pattern.compile("^/topic/board\\.(\\d+)\\.");

    private final WhiteboardService whiteboardService;
    private final UserService userService;

    public WebSocketAuthorizationInterceptor(WhiteboardService whiteboardService, UserService userService) {
        this.whiteboardService = whiteboardService;
        this.userService = userService;
    }

    @Override
    @Nullable
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher matcher = BOARD_TOPIC.matcher(destination);
        if (!matcher.find()) {
            // Not a board-scoped topic (e.g. a user-specific /queue destination) - nothing to gate here.
            return message;
        }

        Long boardId = Long.valueOf(matcher.group(1));
        Principal principal = accessor.getUser();
        if (principal == null || !hasAccess(boardId, principal)) {
            log.debug("Blocked SUBSCRIBE to {} - no access", destination);
            return null; // dropping the message refuses the subscription silently
        }

        return message;
    }

    private boolean hasAccess(Long boardId, Principal principal) {
        User user = userService.findByUsername(principal.getName());
        if (user == null) return false;

        Board board = whiteboardService.getWhiteboardById(boardId);
        if (board == null) return false;

        boolean isOwner = board.getOwnerId() != null && board.getOwnerId().equals(user.getUserId());
        return isOwner || whiteboardService.getUserRoleInWhiteboard(user.getUserId(), boardId) != null;
    }
}
