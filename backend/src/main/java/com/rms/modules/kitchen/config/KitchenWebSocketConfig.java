package com.rms.modules.kitchen.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.messaging.support.MessageHeaderAccessor;
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
    private final String[] allowedOrigins;

    /**
     * @param allowedOrigins origin được mở /ws (rms.web.allowed-origins, env RMS_ALLOWED_ORIGINS, cách nhau
     *                       bởi dấu phẩy). Nhận cả mẫu như https://*.example.com.
     */
    public KitchenWebSocketConfig(
            JwtService jwt,
            UserRepository users,
            @Value("${rms.web.allowed-origins}") String[] allowedOrigins) {
        this.jwt = jwt;
        this.users = users;
        this.allowedOrigins = Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Trình duyệt luôn gửi Origin khi mở WebSocket; origin lạ bị Spring trả 403 lúc bắt tay.
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                // Phải lấy accessor GỐC của message: Spring gắn callback "nhớ user theo phiên" vào đó.
                // StompHeaderAccessor.wrap() tạo accessor mới nên setUser() không tới được phiên.
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
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
