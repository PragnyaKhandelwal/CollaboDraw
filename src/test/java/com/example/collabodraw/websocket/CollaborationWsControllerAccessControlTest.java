package com.example.collabodraw.websocket;

import com.example.collabodraw.controller.CollaborationWsController;
import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.repository.CursorRepository;
import com.example.collabodraw.repository.SessionRepository;
import com.example.collabodraw.realtime.InMemoryEventStore;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the board-membership/role gating added to CollaborationWsController.
 * Prior to this, join/leave/heartbeat/cursor/version/element trusted the client-supplied
 * {boardId} with no check at all - any authenticated user could join, watch, or write into
 * any board on the server just by guessing a numeric id. These tests exercise the handler
 * methods directly against mocked collaborators so they don't need a live DB, WebSocket
 * transport, or HTTP session - just proof that the gating logic itself is correct.
 */
class CollaborationWsControllerAccessControlTest {

    private static final Long BOARD_ID = 42L;

    private SimpMessagingTemplate messagingTemplate;
    private WhiteboardService whiteboardService;
    private UserService userService;
    private CollaborationWsController controller;

    private final Principal owner = () -> "owner";
    private final Principal editor = () -> "editor";
    private final Principal viewer = () -> "viewer";
    private final Principal stranger = () -> "stranger";

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        SessionRepository sessionRepository = mock(SessionRepository.class);
        CursorRepository cursorRepository = mock(CursorRepository.class);
        userService = mock(UserService.class);
        whiteboardService = mock(WhiteboardService.class);
        InMemoryEventStore eventStore = new InMemoryEventStore();

        controller = new CollaborationWsController(messagingTemplate, sessionRepository, cursorRepository,
                userService, whiteboardService, eventStore);

        Board board = new Board();
        board.setBoardId(BOARD_ID);
        board.setOwnerId(1L);
        when(whiteboardService.getWhiteboardById(BOARD_ID)).thenReturn(board);

        registerUser("owner", 1L);
        registerUser("editor", 2L);
        registerUser("viewer", 3L);
        registerUser("stranger", 99L); // exists as a user, but has no relationship to BOARD_ID

        when(whiteboardService.getUserRoleInWhiteboard(2L, BOARD_ID)).thenReturn("editor");
        when(whiteboardService.getUserRoleInWhiteboard(3L, BOARD_ID)).thenReturn("viewer");
        when(whiteboardService.getUserRoleInWhiteboard(99L, BOARD_ID)).thenReturn(null);
    }

    private void registerUser(String username, Long id) {
        User user = new User();
        user.setUsername(username);
        user.setUserId(id);
        when(userService.findByUsername(username)).thenReturn(user);
    }

    private CollaborationWsController.CursorMessage cursorMessage() {
        CollaborationWsController.CursorMessage msg = new CollaborationWsController.CursorMessage();
        msg.x = 10;
        msg.y = 20;
        msg.clientId = "client-1";
        return msg;
    }

    private CollaborationWsController.ElementMessage elementMessage() {
        CollaborationWsController.ElementMessage msg = new CollaborationWsController.ElementMessage();
        msg.kind = "stroke";
        msg.payload = java.util.Map.of("x", 1, "y", 2);
        return msg;
    }

    @Test
    void cursor_unauthenticatedCaller_isDropped() {
        controller.cursor(BOARD_ID, cursorMessage(), null, "sess-1");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void cursor_authenticatedNonMember_isDropped() {
        controller.cursor(BOARD_ID, cursorMessage(), stranger, "sess-1");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void cursor_boardMember_isBroadcast() {
        controller.cursor(BOARD_ID, cursorMessage(), editor, "sess-1");

        verify(messagingTemplate).convertAndSend(eq("/topic/board." + BOARD_ID + ".cursors"), any(Object.class));
    }

    @Test
    void cursor_viewerCanStillWatch() {
        // Viewers may not write drawing data, but presence/cursor is a read-oriented action -
        // a viewer should still be visible to collaborators.
        controller.cursor(BOARD_ID, cursorMessage(), viewer, "sess-1");

        verify(messagingTemplate).convertAndSend(eq("/topic/board." + BOARD_ID + ".cursors"), any(Object.class));
    }

    @Test
    void element_unauthenticatedCaller_isDropped() {
        controller.element(BOARD_ID, elementMessage(), null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void element_authenticatedNonMember_isDropped() {
        controller.element(BOARD_ID, elementMessage(), stranger);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void element_viewerWrite_isRejected() {
        // The core "view-only sharing is enforced client-side only" gap: a viewer must not be
        // able to inject drawing data over the WebSocket even though they can join the board.
        controller.element(BOARD_ID, elementMessage(), viewer);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void element_editorWrite_isBroadcast() {
        controller.element(BOARD_ID, elementMessage(), editor);

        verify(messagingTemplate).convertAndSend(eq("/topic/board." + BOARD_ID + ".elements"), any(Object.class));
    }

    @Test
    void element_ownerWrite_isBroadcast() {
        controller.element(BOARD_ID, elementMessage(), owner);

        verify(messagingTemplate).convertAndSend(eq("/topic/board." + BOARD_ID + ".elements"), any(Object.class));
    }
}
