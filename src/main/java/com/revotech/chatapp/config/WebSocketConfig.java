package com.revotech.chatapp.config;

import com.revotech.chatapp.security.JwtTokenUtil;
import com.revotech.chatapp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService userDetailsService;

    @Value("${app.websocket.allowed-origins:http://localhost:8080}")
    private String[] allowedOrigins;

    // CRITICAL FIX: Store session data để persist qua events
    private final Map<String, Map<String, Object>> sessionDataStore = new ConcurrentHashMap<>();

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(heartBeatScheduler())
                .setHeartbeatValue(new long[]{10000, 10000});
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS()
                .setSessionCookieNeeded(false)
                .setHeartbeatTime(25000);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String sessionId = accessor.getSessionId();
                    log.debug("Processing CONNECT for session: {}", sessionId);

                    try {
                        String authHeader = accessor.getFirstNativeHeader("Authorization");
                        if (authHeader != null && authHeader.startsWith("Bearer ")) {
                            String token = authHeader.substring(7);

                            if (jwtTokenUtil.validateToken(token)) {
                                String username = jwtTokenUtil.getUsernameFromToken(token);

                                var userDetails = userDetailsService.loadUserByUsername(username);
                                var auth = new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());

                                SecurityContextHolder.getContext().setAuthentication(auth);
                                accessor.setUser(auth);

                                Long userId = ((UserPrincipal) userDetails).getId();

                                // CRITICAL FIX: Store session data persistently
                                Map<String, Object> sessionData = new ConcurrentHashMap<>();
                                sessionData.put("username", username);
                                sessionData.put("userId", userId);
                                sessionDataStore.put(sessionId, sessionData);

                                // ALSO set on accessor for immediate use
                                Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                                if (sessionAttributes == null) {
                                    sessionAttributes = new ConcurrentHashMap<>();
                                    accessor.setSessionAttributes(sessionAttributes);
                                }
                                sessionAttributes.putAll(sessionData);

                                log.info("✅ WebSocket authenticated user: {} with ID: {} for session: {}",
                                        username, userId, sessionId);

                            } else {
                                log.warn("Invalid JWT token for session: {}", sessionId);
                                throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Invalid JWT token");
                            }
                        } else {
                            log.warn("No Authorization header for session: {}", sessionId);
                            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Missing authorization header");
                        }

                    } catch (Exception e) {
                        log.error("WebSocket authentication failed for session {}: {}", sessionId, e.getMessage());
                        throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication failed: " + e.getMessage());
                    }
                }

                return message;
            }
        });
    }

    // CRITICAL: Add method to get session data
    public Map<String, Object> getSessionData(String sessionId) {
        return sessionDataStore.get(sessionId);
    }

    // CRITICAL: Add method to remove session data on disconnect
    public void removeSessionData(String sessionId) {
        sessionDataStore.remove(sessionId);
    }

    @Bean
    public TaskScheduler heartBeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("websocket-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}