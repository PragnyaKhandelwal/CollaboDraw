package com.example.collabodraw.security;

import com.example.collabodraw.controller.DrawingController;
import com.example.collabodraw.model.entity.Board;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.repository.ElementRepository;
import com.example.collabodraw.service.UserService;
import com.example.collabodraw.service.WhiteboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression test for the critical, live-confirmed bug in DrawingController: previously
 * /api/drawings/save-canvas and /api/drawings/load-canvas/{boardId} had no authentication or
 * authorization check at all (both permitAll() at the security-filter level AND no check in
 * the controller), so anyone on the internet could read or overwrite any board's canvas by
 * guessing a sequential board id. This test exercises the controller methods directly against
 * mocked collaborators to confirm every access path is now actually gated.
 */
class DrawingControllerAccessControlTest {

    private static final Long BOARD_ID = 5L;

    private ElementRepository elementRepository;
    private WhiteboardService whiteboardService;
    private UserService userService;
    private DrawingController controller;

    @BeforeEach
    void setUp() {
        elementRepository = mock(ElementRepository.class);
        whiteboardService = mock(WhiteboardService.class);
        userService = mock(UserService.class);
        controller = new DrawingController(elementRepository, whiteboardService, userService);

        Board board = new Board();
        board.setBoardId(BOARD_ID);
        board.setOwnerId(1L);
        when(whiteboardService.getWhiteboardById(BOARD_ID)).thenReturn(board);

        User owner = new User();
        owner.setUsername("owner");
        owner.setUserId(1L);
        when(userService.findByUsername("owner")).thenReturn(owner);

        User viewer = new User();
        viewer.setUsername("viewer");
        viewer.setUserId(2L);
        when(userService.findByUsername("viewer")).thenReturn(viewer);
        when(whiteboardService.getUserRoleInWhiteboard(2L, BOARD_ID)).thenReturn("viewer");

        User stranger = new User();
        stranger.setUsername("stranger");
        stranger.setUserId(3L);
        when(userService.findByUsername("stranger")).thenReturn(stranger);
        when(whiteboardService.getUserRoleInWhiteboard(3L, BOARD_ID)).thenReturn(null);
    }

    private Authentication authFor(String username) {
        return new UsernamePasswordAuthenticationToken(username, "n/a", AuthorityUtils.NO_AUTHORITIES);
    }

    private Authentication anonymous() {
        return new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }

    @Test
    void loadCanvas_unauthenticated_isForbidden() {
        ResponseEntity<?> response = controller.loadCanvas(BOARD_ID, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(elementRepository, never()).findByBoardIdAndType(anyLongCompat(), anyStringCompat());
    }

    @Test
    void loadCanvas_anonymousPrincipal_isForbidden() {
        ResponseEntity<?> response = controller.loadCanvas(BOARD_ID, anonymous());

        // AnonymousAuthenticationToken.isAuthenticated() is true, so this must be rejected by
        // the "user not found" path, not the null-authentication path - both must be covered.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void loadCanvas_authenticatedNonMember_isForbidden() {
        ResponseEntity<?> response = controller.loadCanvas(BOARD_ID, authFor("stranger"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void loadCanvas_boardMember_isAllowed() {
        ResponseEntity<?> response = controller.loadCanvas(BOARD_ID, authFor("viewer"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void saveCanvas_unauthenticated_isForbiddenAndNeverWrites() {
        ResponseEntity<?> response = controller.saveCanvas(BOARD_ID, "data:image/png;base64,x", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(elementRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(elementRepository, never()).updateElement(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveCanvas_viewerCannotWrite() {
        ResponseEntity<?> response = controller.saveCanvas(BOARD_ID, "data:image/png;base64,x", authFor("viewer"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(elementRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(elementRepository, never()).updateElement(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveCanvas_owner_isAllowedToWrite() {
        when(elementRepository.findByBoardIdAndType(BOARD_ID, "canvas_image")).thenReturn(null);

        ResponseEntity<?> response = controller.saveCanvas(BOARD_ID, "data:image/png;base64,x", authFor("owner"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) response.getBody()).get("success")).isEqualTo(true);
    }

    // Small helpers to avoid importing ArgumentMatchers.anyLong()/anyString() alongside any()
    // in a way that trips Mockito's mixed-matcher validation.
    private static Long anyLongCompat() { return org.mockito.ArgumentMatchers.anyLong(); }
    private static String anyStringCompat() { return org.mockito.ArgumentMatchers.anyString(); }
}
