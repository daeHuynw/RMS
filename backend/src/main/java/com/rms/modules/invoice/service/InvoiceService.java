package com.rms.modules.invoice.service;

import com.rms.exception.ApiException;
import com.rms.modules.invoice.dto.InvoiceResponse;
import com.rms.modules.invoice.entity.Invoice;
import com.rms.modules.invoice.repository.InvoiceRepository;
import com.rms.modules.order.entity.Order;
import com.rms.modules.order.repository.OrderRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final OrderRepository orders;

    public InvoiceService(InvoiceRepository invoices, OrderRepository orders) {
        this.invoices = invoices;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id, Authentication authentication) {
        Invoice invoice = invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice not found"));
        assertReadable(invoice.getId(), authentication);
        return InvoiceResponse.from(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse byOrder(Long orderId, Authentication authentication) {
        Invoice invoice = invoices.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
        assertReadable(invoice.getId(), authentication);
        return InvoiceResponse.from(invoice);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long id, Authentication authentication) {
        Invoice invoice = invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice not found"));
        assertReadable(invoice.getId(), authentication);
        Order order = orders.findWithDetailsById(invoice.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        return InvoicePdf.write(invoice, order);
    }

    private void assertReadable(Long invoiceId, Authentication authentication) {
        boolean staff = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_STAFF") || a.equals("ROLE_ADMIN"));
        if (staff) {
            return;
        }
        if (!invoices.existsByIdAndOrderUserId(invoiceId, (Long) authentication.getPrincipal())) {
            throw ApiException.forbidden("Invoice does not belong to current user");
        }
    }
}
