package com.rms.modules.payment.controller;

import com.rms.modules.payment.dto.PaymentDtos.RecordRequest;
import com.rms.modules.payment.dto.PaymentDtos.Response;
import com.rms.modules.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/staff")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/orders/{orderId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public Response record(
            @PathVariable Long orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RecordRequest request) {
        return payments.record(orderId, idempotencyKey, request);
    }

    @GetMapping("/payments/{id}")
    public Response get(@PathVariable Long id) {
        return payments.get(id);
    }

    @GetMapping("/orders/{orderId}/payments")
    public List<Response> history(@PathVariable Long orderId) {
        return payments.history(orderId);
    }
}
