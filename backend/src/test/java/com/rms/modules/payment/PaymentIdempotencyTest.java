package com.rms.modules.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.invoice.repository.InvoiceRepository;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderStatus;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.payment.dto.PaymentDtos.RecordRequest;
import com.rms.modules.payment.entity.Payment;
import com.rms.modules.payment.entity.PaymentMethod;
import com.rms.modules.payment.entity.PaymentStatus;
import com.rms.modules.payment.repository.PaymentRepository;
import com.rms.modules.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * REVIEW C4 — gửi lại cùng Idempotency-Key khi thanh toán phải nhận lại đúng kết quả cũ, kể cả khi
 * các yêu cầu tới CÙNG LÚC. Muốn vậy, việc tra key phải nằm SAU khoá dòng đơn: yêu cầu thứ hai chờ khoá,
 * và khi được khoá thì thanh toán của yêu cầu đầu đã commit nên tra key sẽ thấy.
 * (Trước đây tra key trước khi khoá: yêu cầu thứ hai không thấy gì, rồi vấp "đơn không còn SERVED" → 409.)
 */
class PaymentIdempotencyTest {

    private static final long ORDER_ID = 26L;
    private static final String KEY = "pay-key-1";
    private static final BigDecimal TOTAL = new BigDecimal("48600.00");

    private OrderRepository orders;
    private PaymentRepository payments;
    private InvoiceRepository invoices;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        payments = mock(PaymentRepository.class);
        invoices = mock(InvoiceRepository.class);
        when(invoices.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service = new PaymentService(
                orders, payments, invoices, mock(AuditService.class),
                mock(NotificationService.class));
    }

    @Test
    void theKeyIsLookedUpOnlyAfterTheOrderRowIsLocked() {
        order(OrderStatus.SERVED);
        when(payments.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        service.record(ORDER_ID, KEY, request());

        InOrder order = inOrder(orders, payments);
        order.verify(orders).lockById(ORDER_ID);
        order.verify(payments).findByIdempotencyKey(KEY);
    }

    /** Yêu cầu trùng tới sau khi yêu cầu đầu đã thanh toán xong: đơn đã COMPLETED, nhưng vẫn nhận lại kết quả cũ. */
    @Test
    void aDuplicateArrivingAfterTheOrderWasSettledGetsTheOriginalPayment() {
        order(OrderStatus.COMPLETED);
        Payment original = new Payment(ORDER_ID, TOTAL, PaymentMethod.CASH, PaymentStatus.SUCCESS, null, KEY);
        ReflectionTestUtils.setField(original, "id", 8L);
        when(payments.findByIdempotencyKey(KEY)).thenReturn(Optional.of(original));

        var response = service.record(ORDER_ID, KEY, request());

        assertThat(response.id()).isEqualTo(8L);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void aNewKeyOnAnAlreadySettledOrderIsStillRejected() {
        order(OrderStatus.COMPLETED);
        when(payments.findByIdempotencyKey("another-key")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(ORDER_ID, "another-key", request()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ORDER_NOT_PAYABLE"));
    }

    @Test
    void aKeyUsedForAnotherOrderIsRejected() {
        order(OrderStatus.SERVED);
        Payment elsewhere = new Payment(99L, TOTAL, PaymentMethod.CASH, PaymentStatus.SUCCESS, null, KEY);
        when(payments.findByIdempotencyKey(KEY)).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> service.record(ORDER_ID, KEY, request()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    }

    private void order(OrderStatus status) {
        Order order = new Order("ORD-TEST", null, null, null, "order-key", null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.reprice(new BigDecimal("45000.00"), BigDecimal.ZERO, new BigDecimal("3600.00"));
        order.transition(status, null);
        when(orders.lockById(ORDER_ID)).thenReturn(Optional.of(order));
    }

    private static RecordRequest request() {
        return new RecordRequest(TOTAL, PaymentMethod.CASH, null);
    }
}
