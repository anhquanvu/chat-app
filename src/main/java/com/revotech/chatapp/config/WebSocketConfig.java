package com.revotech.chatapp.config;

import com.revotech.chatapp.security.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService userDetailsService;

    @Value("${app.websocket.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/user");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    return handleConnectionAuthentication(accessor, message);
                }

                return message;
            }
        });
    }

    private Message<?> handleConnectionAuthentication(StompHeaderAccessor accessor, Message<?> message) {
        String sessionId = accessor.getSessionId();

        try {
            String authToken = accessor.getFirstNativeHeader("Authorization");

            if (authToken != null && authToken.startsWith("Bearer ")) {
                String token = authToken.substring(7);

                if (jwtTokenUtil.validateToken(token)) {
                    String username = jwtTokenUtil.getUsernameFromToken(token);

                    var userDetails = userDetailsService.loadUserByUsername(username);
                    var auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

                    SecurityContextHolder.getContext().setAuthentication(auth);
                    accessor.setUser(auth);

                    // Ensure session attributes are properly set
                    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                    if (sessionAttributes != null) {
                        sessionAttributes.put("username", username);
                        sessionAttributes.put("userId", ((com.revotech.chatapp.security.UserPrincipal) userDetails).getId());

                        log.info("WebSocket authenticated user: {} with ID: {} for session: {}",
                                username, ((com.revotech.chatapp.security.UserPrincipal) userDetails).getId(), sessionId);
                    } else {
                        log.warn("Session attributes is null during authentication for user: {} session: {}", username, sessionId);
                    }
                } else {
                    log.warn("Invalid JWT token received for session: {}", sessionId);
                    throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Invalid JWT token");
                }
            } else {
                log.warn("No Authorization header found for session: {}", sessionId);
                throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Missing authorization header");
            }
        } catch (Exception e) {
            log.error("WebSocket authentication failed for session {}: {}", sessionId, e.getMessage());
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication failed: " + e.getMessage());
        }

        return message;
    }
}