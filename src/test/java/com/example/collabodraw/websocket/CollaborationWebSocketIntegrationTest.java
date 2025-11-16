package com.example.collabodraw.websocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CollaborationWebSocketIntegrationTest {

    @LocalServerPort
    int port;

    private WebSocketStompClient stompClient;
    private StompSession session;

    @BeforeEach
    void setup() throws Exception {
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

    // Deprecated connect API used for simplicity; if upgraded, switch to CompletableFuture-based API.
    ListenableFuture<StompSession> future = stompClient.connect(getWsUrl(), new WebSocketHttpHeaders(), new StompSessionHandlerAdapter() {});
    session = future.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    private String getWsUrl() {
        return "http://localhost:" + port + "/ws";
    }

    @Test
    void cursorMessageBroadcastsToTopic() throws Exception {
        // Subscribe to cursor topic for boardId=1
        CompletableFuture<Map<String, Object>> received = new CompletableFuture<>();
        session.subscribe("/topic/board.1.cursors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(@org.springframework.lang.NonNull StompHeaders headers) {
                return Map.class;
            }
            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(@org.springframework.lang.NonNull StompHeaders headers, @org.springframework.lang.Nullable Object payload) {
                received.complete((Map<String, Object>) payload);
            }
        });

        // Send a cursor update via app destination
        String dest = "/app/board/1/cursor";
        Map<String, Object> cursorMsg = Map.of("x", 42, "y", 21);
        session.send(dest, cursorMsg);

        Map<String, Object> event = received.get(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("cursor");
        assertThat(event.get("x")).isEqualTo(42);
        assertThat(event.get("y")).isEqualTo(21);
    }
}
