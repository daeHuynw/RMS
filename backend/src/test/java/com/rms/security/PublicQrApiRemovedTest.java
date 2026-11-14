package com.rms.security;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rms.common.GlobalExceptionHandler;
import com.rms.config.SecurityConfig;
import com.rms.modules.order.service.OrderService;
import com.rms.modules.table.service.TableService;
import com.rms.modules.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * REVIEW B1 — luồng đã chốt là "không gọi món bằng QR": không ai (kể cả người đã đăng nhập)
 * được mở phiên bàn hay tạo đơn qua /api/v1/qr/**.
 *
 * Khác các test standalone: test này chạy qua SecurityFilterChain thật (SecurityConfig).
 * Controller được nạp theo tên (regex) chứ không import class, để test vẫn biên dịch
 * sau khi các controller QR bị xoá.
 */
@WebMvcTest(
        useDefaultFilters = false,
        includeFilters = {
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.rms\\.modules\\..*\\.controller\\.Qr.*"),
            @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
        })
@Import(SecurityConfig.class)
class PublicQrApiRemovedTest {

    private static final String ORDER_BODY = """
            {"items":[{"menuItemId":1,"quantity":1}]}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtService jwt;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private TableService tables;

    @MockitoBean
    private OrderService orders;

    @Test
    void anonymousCannotOpenATableSessionThroughQrMenu() throws Exception {
        mvc.perform(get("/api/v1/qr/menu").param("token", "any-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(tables);
    }

    @Test
    void anonymousCannotOpenATableSessionThroughQrSession() throws Exception {
        mvc.perform(get("/api/v1/qr/session").param("token", "any-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tables);
    }

    @Test
    void anonymousCannotCreateAnOrderThroughQr() throws Exception {
        mvc.perform(post("/api/v1/qr/orders")
                        .param("token", "any-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(orders);
    }

    @Test
    void qrMenuNoLongerExistsEvenForLoggedInCustomer() throws Exception {
        mvc.perform(get("/api/v1/qr/menu").param("token", "any-token").with(authentication(customer())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verifyNoInteractions(tables);
    }

    @Test
    void qrOrderNoLongerExistsEvenForLoggedInCustomer() throws Exception {
        mvc.perform(post("/api/v1/qr/orders")
                        .param("token", "any-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY)
                        .with(authentication(customer())))
                .andExpect(status().isNotFound());

        verifyNoInteractions(orders);
    }

    private static UsernamePasswordAuthenticationToken customer() {
        return new UsernamePasswordAuthenticationToken(7L, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
