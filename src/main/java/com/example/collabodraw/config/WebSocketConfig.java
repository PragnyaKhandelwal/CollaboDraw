package com.example.collabodraw.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Shares the same allow-list as HTTP CORS (see SecurityConfig). The previous "*" here
    // let any website open a WebSocket handshake against this endpoint using a visitor's
    // logged-in session cookie (cross-site WebSocket hijacking) - SockJS handshakes aren't
    // covered by normal CORS/fetch same-origin rules, so this allow-list is the actual defense.
    @Value("${app.cors.allowed-origins:http://localhost:8080,http://localhost:3000,http://127.0.0.1:8080}")
    private String allowedOriginsProperty;

    // See application.properties for the rationale. Off by default (SimpleBroker); flipping
    // app.stomp.relay.enabled=true at deploy time moves broker state out of this process so
    // multiple instances can share it, with no code change.
    @Value("${app.stomp.relay.enabled:false}")
    private boolean stompRelayEnabled;
    @Value("${app.stomp.relay.host:localhost}")
    private String stompRelayHost;
    @Value("${app.stomp.relay.port:61613}")
    private int stompRelayPort;
    @Value("${app.stomp.relay.login:guest}")
    private String stompRelayLogin;
    @Value("${app.stomp.relay.passcode:guest}")
    private String stompRelayPasscode;

    private final WebSocketAuthorizationInterceptor authorizationInterceptor;

    public WebSocketConfig(WebSocketAuthorizationInterceptor authorizationInterceptor) {
        this.authorizationInterceptor = authorizationInterceptor;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authorizationInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        if (stompRelayEnabled) {
            registry.enableStompBrokerRelay("/topic", "/queue")
                    .setRelayHost(stompRelayHost)
                    .setRelayPort(stompRelayPort)
                    .setClientLogin(stompRelayLogin)
                    .setClientPasscode(stompRelayPasscode)
                    .setSystemLogin(stompRelayLogin)
                    .setSystemPasscode(stompRelayPasscode);
        } else {
            registry.enableSimpleBroker("/topic", "/queue");
        }
        registry.setApplicationDestinationPrefixes("/app");
    }
}
