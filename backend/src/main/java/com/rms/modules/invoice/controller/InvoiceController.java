package com.rms.modules.invoice.controller;

import com.rms.modules.invoice.dto.InvoiceResponse;
import com.rms.modules.invoice.service.InvoiceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable Long id, Authentication authentication) {
        return invoices.get(id, authentication);
    }

    @GetMapping("/by-order/{orderId}")
    public InvoiceResponse byOrder(@PathVariable Long orderId, Authentication authentication) {
        return invoices.byOrder(orderId, authentication);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, Authentication authentication) {
        InvoiceResponse meta = invoices.get(id, authentication);
        byte[] body = invoices.pdf(id, authentication);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.invoiceNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }
}
