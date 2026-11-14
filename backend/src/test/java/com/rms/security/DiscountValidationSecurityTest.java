package com.rms.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rms.common.GlobalExceptionHandler;
import com.rms.config.SecurityConfig;
import com.rms.modules.discount.controller.DiscountValidationController;
import com.rms.modules.discount.dto.DiscountDtos.Quote;
import com.rms.modules.discount.service.DiscountService;
import com.rms.modules.user.repository.UserRepository;
import java.math.BigDecimal;
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
 * REVIEW B11 — POST /api/v1/discounts/validate trả lời "mã này có hợp lệ không".
 * Mở công khai thì ai cũng dò được mã; phải đăng nhập mới được hỏi.
 * Chạy qua SecurityFilterChain thật (xem PublicQrApiRemovedTest).
 */
@WebMvcTest(
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                    DiscountValidationController.class,
                    JwtAuthenticationFilter.class,
                    GlobalExceptionHandler.class
                }))
@Import(SecurityConfig.class)
class DiscountValidationSecurityTest {

    private static final String BODY = """
            {"code":"SALE10","subtotal":100000}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtService jwt;

    @MockitoBean
    private UserRepository users;

    @MockitoBean
    private DiscountService discounts;

    @Test
    void anonymousCannotProbeDiscountCodes() throws Exception {
        mvc.perform(post("/api/v1/discounts/validate").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(discounts);
    }

    @Test
    void loggedInUserCanStillValidateACode() throws Exception {
        when(discounts.validateCode(any(), any())).thenReturn(new Quote("SALE10", new BigDecimal("10000")));
        var customer = new UsernamePasswordAuthenticationToken(
                7L, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        mvc.perform(post("/api/v1/discounts/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(authentication(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SALE10"));
    }
}
