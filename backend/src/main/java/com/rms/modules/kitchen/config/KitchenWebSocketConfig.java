package com.rms.modules.kitchen.config;

import com.rms.modules.user.repository.UserRepository;
import com.rms.security.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class KitchenWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwt;
    private final UserRepository users;

    public KitchenWebSocketConfig(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (accessor.getCommand() == StompCommand.CONNECT) {
                    String authorization = accessor.getFirstNativeHeader("Authorization");
                    if (authorization == null || !authorization.startsWith("Bearer ")) {
                        throw new AccessDeniedException("Bearer token required");
                    }
                    long userId;
                    try {
                        userId = jwt.parseUserId(authorization.substring(7));
                    } catch (RuntimeException ex) {
                        throw new AccessDeniedException("Invalid bearer token");
                    }
                    var user = users.findWithRolesById(userId)
                            .filter(com.rms.modules.user.entity.User::isActive)
                            .orElseThrow(() -> new AccessDeniedException("Active user required"));
                    var authorities = user.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                            .toList();
                    if (authorities.stream().noneMatch(KitchenWebSocketConfig::isKitchenRole)) {
                        throw new AccessDeniedException("Kitchen role required");
                    }
                    accessor.setUser(new UsernamePasswordAuthenticationToken(user.getId(), null, authorities));
                }
                if (accessor.getCommand() == StompCommand.SUBSCRIBE
                        && "/topic/kitchen/orders".equals(accessor.getDestination())) {
                    var principal = accessor.getUser();
                    if (!(principal instanceof org.springframework.security.core.Authentication auth)
                            || auth.getAuthorities().stream().noneMatch(KitchenWebSocketConfig::isKitchenRole)) {
                        throw new AccessDeniedException("Kitchen subscription requires kitchen/staff role");
                    }
                }
                return message;
            }
        });
    }

    private static boolean isKitchenRole(org.springframework.security.core.GrantedAuthority authority) {
        String role = authority.getAuthority();
        return "ROLE_KITCHEN".equals(role) || "ROLE_STAFF".equals(role) || "ROLE_ADMIN".equals(role);
    }
}
