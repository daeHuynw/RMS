package com.rms.modules.invoice.dto;

import com.rms.modules.invoice.entity.Invoice;
import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceResponse(
        Long id,
        Long orderId,
        String invoiceNumber,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        Instant issuedAt) {

    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getOrderId(),
                invoice.getInvoiceNumber(),
                invoice.getSubtotal(),
                invoice.getDiscountAmount(),
                invoice.getTaxAmount(),
                invoice.getTotalAmount(),
                invoice.getIssuedAt());
    }
}
