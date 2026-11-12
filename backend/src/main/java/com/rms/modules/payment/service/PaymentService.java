package com.rms.modules.payment.service;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.invoice.entity.Invoice;
import com.rms.modules.invoice.repository.InvoiceRepository;
import com.rms.modules.notification.service.NotificationService;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.entity.OrderStatus;
import com.rms.modules.order.repository.OrderRepository;
import com.rms.modules.payment.dto.PaymentDtos.RecordRequest;
import com.rms.modules.payment.dto.PaymentDtos.Response;
import com.rms.modules.payment.entity.Payment;
import com.rms.modules.payment.entity.PaymentStatus;
import com.rms.modules.payment.repository.PaymentRepository;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final AuditService audit;
    private final NotificationService notifications;

    public PaymentService(
            OrderRepository orders,
            PaymentRepository payments,
            InvoiceRepository invoices,
            AuditService audit,
            NotificationService notifications) {
        this.orders = orders;
        this.payments = payments;
        this.invoices = invoices;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Transactional
    public Response record(Long orderId, String idempotencyKey, RecordRequest request) {
        String key = requireIdempotencyKey(idempotencyKey);
        var existing = payments.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (!payment.getOrderId().equals(orderId)) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was already used");
            }
            Invoice invoice = invoices.findByOrderId(orderId).orElse(null);
            return Response.from(
                    payment,
                    invoice == null ? null : invoice.getId(),
                    invoice == null ? null : invoice.getInvoiceNumber());
        }

        Order order = orders.lockById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.getStatus() != OrderStatus.SERVED) {
            throw ApiException.conflict("ORDER_NOT_PAYABLE", "Only SERVED orders can be settled");
        }
        if (payments.existsByOrderIdAndStatus(orderId, PaymentStatus.SUCCESS)) {
            throw ApiException.conflict("ORDER_ALREADY_PAID", "Order already has a successful payment");
        }
        if (request.amount().compareTo(order.getTotalAmount()) != 0) {
            throw ApiException.badRequest("PAYMENT_AMOUNT_MISMATCH", "Payment amount must equal order total");
        }

        String reference = request.reference() == null || request.reference().isBlank()
                ? null
                : request.reference().trim();
        try {
            Payment payment = payments.saveAndFlush(new Payment(
                    orderId,
                    request.amount(),
                    request.method(),
                    PaymentStatus.SUCCESS,
                    reference,
                    key));
            order.settle();
            Invoice invoice = invoices.saveAndFlush(new Invoice(
                    orderId,
                    nextInvoiceNumber(),
                    order.getSubtotal(),
                    order.getDiscountAmount(),
                    order.getTaxAmount(),
                    order.getTotalAmount()));
            audit.record(
                    "PAYMENT_RECORDED",
                    "ORDER",
                    String.valueOf(orderId),
                    request.method().name() + " " + request.amount());
            if (order.getUser() != null) {
                notifications.notifyUser(
                        order.getUser().getId(),
                        "Đã thanh toán",
                        "Đơn " + order.getOrderNumber() + " · " + invoice.getInvoiceNumber(),
                        "PAYMENT");
            }
            return Response.from(payment, invoice.getId(), invoice.getInvoiceNumber());
        } catch (DataIntegrityViolationException ex) {
            return payments.findByIdempotencyKey(key)
                    .map(p -> {
                        Invoice invoice = invoices.findByOrderId(orderId).orElse(null);
                        return Response.from(
                                p,
                                invoice == null ? null : invoice.getId(),
                                invoice == null ? null : invoice.getInvoiceNumber());
                    })
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public Response get(Long id) {
        Payment payment = payments.findById(id).orElseThrow(() -> ApiException.notFound("Payment not found"));
        Invoice invoice = invoices.findByOrderId(payment.getOrderId()).orElse(null);
        return Response.from(
                payment,
                invoice == null ? null : invoice.getId(),
                invoice == null ? null : invoice.getInvoiceNumber());
    }

    @Transactional(readOnly = true)
    public List<Response> history(Long orderId) {
        return payments.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(p -> {
                    Invoice invoice = invoices.findByOrderId(orderId).orElse(null);
                    return Response.from(
                            p,
                            invoice == null ? null : invoice.getId(),
                            invoice == null ? null : invoice.getInvoiceNumber());
                })
                .toList();
    }

    private static String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 120) {
            throw ApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "A valid Idempotency-Key header is required");
        }
        return key.trim();
    }

    private static String nextInvoiceNumber() {
        return "INV-" + Long.toUnsignedString(new SecureRandom().nextLong(), 36).toUpperCase();
    }
}
