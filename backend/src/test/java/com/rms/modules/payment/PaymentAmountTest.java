package com.rms.modules.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.invoice.entity.Invoice;
import com.rms.modules.invoice.repository.InvoiceRepository;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderStatus;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.payment.dto.PaymentDtos.RecordRequest;
import com.rms.modules.payment.entity.PaymentMethod;
import com.rms.modules.payment.repository.PaymentRepository;
import com.rms.modules.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW C2 — thu tiền: số tiền phải bằng đúng tổng đơn, và hoá đơn chép nguyên các con số của đơn
 * (không tính lại). Đơn mẫu là ca chuẩn của mục D: 155 000 − 15 500 + 11 160 = 150 660.
 */
class PaymentAmountTest {

    private static final long ORDER_ID = 26L;

    private OrderRepository orders;
    private PaymentRepository payments;
    private InvoiceRepository invoices;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        payments = mock(PaymentRepository.class);
        invoices = mock(InvoiceRepository.class);
        when(payments.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(invoices.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service = new PaymentService(
                orders, payments, invoices, mock(AuditService.class), mock(NotificationService.class));

        Order order = new Order("ORD-D", null, null, null, "order-key", null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.reprice(new BigDecimal("155000.00"), new BigDecimal("15500.00"), new BigDecimal("11160.00"));
        order.transition(OrderStatus.SERVED, null);
        when(orders.lockById(ORDER_ID)).thenReturn(Optional.of(order));
    }

    @Test
    void theInvoiceCopiesTheOrdersFigures() {
        service.record(ORDER_ID, "pay-1", pay("150660"));

        ArgumentCaptor<Invoice> saved = ArgumentCaptor.forClass(Invoice.class);
        verify(invoices).saveAndFlush(saved.capture());
        Invoice invoice = saved.getValue();
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("155000");
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("15500");
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo("11160");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("150660");
    }

    @Test
    void theAmountMayDifferOnlyInScale() {
        // 150660 và 150660.00 là cùng một số tiền
        service.record(ORDER_ID, "pay-1", pay("150660.00"));

        verify(payments).saveAndFlush(any());
    }

    @Test
    void anyOtherAmountIsRejectedAndNothingIsRecorded() {
        for (String wrong : new String[] {"150659.99", "150661", "155000"}) {
            assertThatThrownBy(() -> service.record(ORDER_ID, "pay-" + wrong, pay(wrong)))
                    .as(wrong)
                    .isInstanceOfSatisfying(ApiException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getCode()).isEqualTo("PAYMENT_AMOUNT_MISMATCH");
                    });
        }
        verify(payments, never()).saveAndFlush(any());
        verify(invoices, never()).saveAndFlush(any());
    }

    private static RecordRequest pay(String amount) {
        return new RecordRequest(new BigDecimal(amount), PaymentMethod.CASH, null);
    }
}
