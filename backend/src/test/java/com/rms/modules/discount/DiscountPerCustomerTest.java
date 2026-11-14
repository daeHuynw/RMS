package com.rms.modules.discount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.discount.entity.Discount;
import com.rms.modules.discount.repository.DiscountRepository;
import com.rms.modules.discount.repository.OrderDiscountRepository;
import com.rms.modules.discount.service.DiscountService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW B11 — mỗi tài khoản khách chỉ dùng mỗi mã giảm giá một lần (đơn đã huỷ không tính).
 * Đơn staff tạo cho khách vãng lai (không có tài khoản khách) không bị giới hạn này.
 */
class DiscountPerCustomerTest {

    private static final long DISCOUNT_ID = 9L;
    private static final long CUSTOMER_ID = 20L;
    private static final BigDecimal SUBTOTAL = new BigDecimal("100000");

    private OrderDiscountRepository orderDiscounts;
    private DiscountService service;
    private Discount discount;

    @BeforeEach
    void setUp() {
        DiscountRepository discounts = mock(DiscountRepository.class);
        orderDiscounts = mock(OrderDiscountRepository.class);
        service = new DiscountService(discounts, orderDiscounts, mock(AuditService.class));

        Instant now = Instant.now();
        discount = new Discount(
                "SALE10", null, Discount.Type.PERCENTAGE, new BigDecimal("10"), null, null, 100,
                now.minusSeconds(3600), now.plusSeconds(3600));
        ReflectionTestUtils.setField(discount, "id", DISCOUNT_ID);
        when(discounts.lockByCode("SALE10")).thenReturn(Optional.of(discount));
    }

    @Test
    void aCustomerCannotUseTheSameCodeTwice() {
        when(orderDiscounts.existsActiveUseByCustomer(DISCOUNT_ID, CUSTOMER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.lockApplicable("sale10", SUBTOTAL, CUSTOMER_ID))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("DISCOUNT_ALREADY_USED");
                });
    }

    @Test
    void aCustomerCanUseACodeTheyHaveNotUsedYet() {
        when(orderDiscounts.existsActiveUseByCustomer(DISCOUNT_ID, CUSTOMER_ID)).thenReturn(false);

        assertThat(service.lockApplicable("sale10", SUBTOTAL, CUSTOMER_ID)).isSameAs(discount);
    }

    @Test
    void walkInOrdersCreatedByStaffAreNotLimitedPerCustomer() {
        when(orderDiscounts.existsActiveUseByCustomer(anyLong(), any())).thenReturn(true);

        assertThat(service.lockApplicable("sale10", SUBTOTAL, null)).isSameAs(discount);
        verify(orderDiscounts, never()).existsActiveUseByCustomer(anyLong(), any());
    }

    @Test
    void anExhaustedCodeIsStillRejectedForEveryone() {
        ReflectionTestUtils.setField(discount, "usedCount", 100);

        assertThatThrownBy(() -> service.lockApplicable("sale10", SUBTOTAL, CUSTOMER_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_DISCOUNT"));
    }
}
