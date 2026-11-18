package com.rms.modules.kitchen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rms.modules.kitchen.config.KitchenWebSocketConfig;
import com.rms.modules.user.repository.UserRepository;
import com.rms.security.JwtService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

/**
 * /ws chỉ nhận kết nối từ các origin được cấu hình (rms.web.allowed-origins / RMS_ALLOWED_ORIGINS),
 * không còn "*". Trình duyệt luôn gửi header Origin khi mở WebSocket; Spring so header đó với danh sách
 * này lúc bắt tay và trả 403 nếu không khớp.
 */
class KitchenWebSocketOriginTest {

    @Test
    void onlyTheConfiguredOriginsMayOpenTheKitchenSocket() {
        List<String> registered = registeredOrigins(
                new String[] {"http://localhost:5173", "http://127.0.0.1:5173"});

        assertThat(registered).containsExactly("http://localhost:5173", "http://127.0.0.1:5173");
        assertThat(registered).doesNotContain("*");
    }

    @Test
    void blankEntriesFromTheEnvironmentAreIgnored() {
        // RMS_ALLOWED_ORIGINS="https://rms.example.com, " → không sinh ra origin rỗng
        List<String> registered = registeredOrigins(new String[] {" https://rms.example.com ", " "});

        assertThat(registered).containsExactly("https://rms.example.com");
    }

    private static List<String> registeredOrigins(String[] configured) {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration endpoint = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/ws")).thenReturn(endpoint);
        List<String> captured = new ArrayList<>();
        when(endpoint.setAllowedOriginPatterns(any(String[].class))).thenAnswer(call -> {
            for (Object origin : call.getArguments()) {
                if (origin instanceof String[] many) {
                    captured.addAll(List.of(many));
                } else {
                    captured.add((String) origin);
                }
            }
            return endpoint;
        });

        new KitchenWebSocketConfig(mock(JwtService.class), mock(UserRepository.class), configured)
                .registerStompEndpoints(registry);
        return captured;
    }
}
