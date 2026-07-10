package com.example.collabodraw.websocket;

import com.example.collabodraw.config.WebSocketAuthorizationInterceptor;
import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WebSocketAuthorizationInterceptor, which blocks STOMP SUBSCRIBE frames to
 * board-scoped topics from callers who aren't a member/owner of that board. This exists
 * because Spring's simple broker routes SUBSCRIBE straight to itself with no per-destination
 * authorization by default - without this interceptor, an authenticated client could
 * SUBSCRIBE directly to /topic/board.{anyId}.elements and silently read another board's live
 * activity without ever calling the join handler (which is where CollaborationWsController's
 * own checks live).
 */
class WebSocketAuthorizationInterceptorTest {

    private static final Long BOARD_ID = 7L;

    private WhiteboardService whiteboardService;
    private UserService userService;
    private WebSocketAuthorizationInterceptor interceptor;
    private MessageChannel mockChannel;

    @BeforeEach
    void setUp() {
        whiteboardService = mock(WhiteboardService.class);
        userService = mock(UserService.class);
        interceptor = new WebSocketAuthorizationInterceptor(whiteboardService, userService);
        mockChannel = mock(MessageChannel.class);

        Board board = new Board();
        board.setBoardId(BOARD_ID);
        board.setOwnerId(1L);
        when(whiteboardService.getWhiteboardById(BOARD_ID)).thenReturn(board);

        User member = new User();
        member.setUsername("member");
        member.setUserId(2L);
        when(userService.findByUsername("member")).thenReturn(member);
        when(whiteboardService.getUserRoleInWhiteboard(2L, BOARD_ID)).thenReturn("viewer");

        User stranger = new User();
        stranger.setUsername("stranger");
        stranger.setUserId(3L);
        when(userService.findByUsername("stranger")).thenReturn(stranger);
        when(whiteboardService.getUserRoleInWhiteboard(3L, BOARD_ID)).thenReturn(null);
    }

    private Message<byte[]> subscribeMessage(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-0");
        if (user != null) accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void subscribeWithoutPrincipal_isBlocked() {
        Message<?> result = interceptor.preSend(subscribeMessage("/topic/board." + BOARD_ID + ".elements", null), mockChannel);
        assertThat(result).isNull();
    }

    @Test
    void subscribeAsNonMember_isBlocked() {
        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/board." + BOARD_ID + ".elements", (Principal) () -> "stranger"), mockChannel);
        assertThat(result).isNull();
    }

    @Test
    void subscribeAsMember_isAllowed() {
        Message<?> input = subscribeMessage("/topic/board." + BOARD_ID + ".elements", (Principal) () -> "member");
        Message<?> result = interceptor.preSend(input, mockChannel);
        assertThat(result).isSameAs(input);
    }

    @Test
    void subscribeToNonBoardTopic_isUnaffected() {
        // e.g. a user-specific /user/queue/... destination - nothing to gate here.
        Message<?> input = subscribeMessage("/user/queue/notifications", null);
        Message<?> result = interceptor.preSend(input, mockChannel);
        assertThat(result).isSameAs(input);
    }

    @Test
    void nonSubscribeCommand_isUnaffected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/board/" + BOARD_ID + "/cursor");
        accessor.setLeaveMutable(true);
        Message<?> input = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(input, mockChannel);
        assertThat(result).isSameAs(input);
    }
}
