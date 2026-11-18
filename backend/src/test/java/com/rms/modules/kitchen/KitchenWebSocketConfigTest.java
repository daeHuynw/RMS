package com.rms.modules.kitchen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rms.modules.kitchen.config.KitchenWebSocketConfig;
import com.rms.modules.role.entity.Role;
import com.rms.modules.user.entity.User;
import com.rms.modules.user.repository.UserRepository;
import com.rms.security.JwtService;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Chạy interceptor của /ws với frame dựng giống cách Spring dựng thật:
 * accessor gốc để mutable và (với CONNECT) có gắn user-change callback.
 * Callback đó là đường duy nhất để user được nhớ theo phiên WebSocket.
 */
class KitchenWebSocketConfigTest {

    private static final String TOPIC = "/topic/kitchen/orders";

    private JwtService jwt;
    private UserRepository users;
    private ChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwt = mock(JwtService.class);
        users = mock(UserRepository.class);
        var registration = new CapturingRegistration();
        new KitchenWebSocketConfig(jwt, users, new String[] {"http://localhost:5173"})
                .configureClientInboundChannel(registration);
        interceptor = registration.captured().get(0);
    }

    @Test
    void connectWithKitchenTokenStoresUserOnSession() {
        givenUser(7L, "good-token", "KITCHEN");
        var remembered = new AtomicReference<Principal>();

        interceptor.preSend(connectFrame("Bearer good-token", remembered), null);

        assertThat(remembered.get())
                .as("user phải được báo về phiên qua callback của accessor gốc")
                .isInstanceOf(Authentication.class);
        var auth = (Authentication) remembered.get();
        assertThat(auth.getPrincipal()).isEqualTo(7L);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_KITCHEN");
    }

    @Test
    void connectAsCustomerIsRejected() {
        givenUser(8L, "customer-token", "CUSTOMER");

        assertThatThrownBy(() -> interceptor.preSend(connectFrame("Bearer customer-token", new AtomicReference<>()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithoutBearerTokenIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(connectFrame(null, new AtomicReference<>()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        when(jwt.parseUserId("bad-token")).thenThrow(new IllegalArgumentException("invalid token"));

        assertThatThrownBy(() -> interceptor.preSend(connectFrame("Bearer bad-token", new AtomicReference<>()), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeWithoutUserIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(subscribeFrame(null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribeWithKitchenUserPasses() {
        var auth = new UsernamePasswordAuthenticationToken(
                7L, null, List.of(new SimpleGrantedAuthority("ROLE_KITCHEN")));
        Message<byte[]> frame = subscribeFrame(auth);

        assertThat(interceptor.preSend(frame, null)).isSameAs(frame);
    }

    private void givenUser(long id, String token, String roleName) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.isActive()).thenReturn(true);
        when(user.getRoles()).thenReturn(Set.of(new Role(roleName)));
        when(jwt.parseUserId(token)).thenReturn(id);
        when(users.findWithRolesById(id)).thenReturn(Optional.of(user));
    }

    private static Message<byte[]> connectFrame(String authorization, AtomicReference<Principal> remembered) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);
        accessor.setUserChangeCallback(remembered::set);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> subscribeFrame(Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(TOPIC);
        accessor.setSubscriptionId("sub-0");
        accessor.setSessionId("session-1");
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    /** ChannelRegistration giấu danh sách interceptor (protected) nên test mở ra để lấy. */
    private static final class CapturingRegistration extends ChannelRegistration {
        List<ChannelInterceptor> captured() {
            return getInterceptors();
        }
    }
}
