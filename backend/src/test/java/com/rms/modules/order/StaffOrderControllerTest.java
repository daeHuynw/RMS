package com.rms.modules.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rms.modules.order.controller.StaffOrderController;
import com.rms.modules.order.dto.OrderDtos.CreateRequest;
import com.rms.modules.order.dto.OrderDtos.Response;
import com.rms.modules.order.entity.OrderStatus;
import com.rms.modules.order.service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StaffOrderControllerTest {

    private OrderService orders;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        orders = mock(OrderService.class);
        mvc = MockMvcBuilders.standaloneSetup(new StaffOrderController(orders)).build();
    }

    @Test
    void createOrderHappyPath() throws Exception {
        var response = new Response(
                99L,
                "ORD-99",
                "DINE_IN",
                OrderStatus.PENDING,
                new BigDecimal("45000"),
                BigDecimal.ZERO,
                new BigDecimal("3600"),
                new BigDecimal("48600"),
                null,
                null,
                Instant.parse("2026-01-01T10:00:00Z"),
                3L,
                5L,
                "T01",
                List.of());

        when(orders.createFromStaff(eq(5L), any(CreateRequest.class), isNull(), eq(3L)))
                .thenReturn(response);

        var authentication = new UsernamePasswordAuthenticationToken(3L, null, List.of());

        mvc.perform(post("/api/v1/staff/orders")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tableSessionId": 5,
                                  "items": [{"menuItemId": 10, "quantity": 1, "note": null}],
                                  "customerNote": null,
                                  "discountCode": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.orderNumber").value("ORD-99"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.tableCode").value("T01"));
    }
}
