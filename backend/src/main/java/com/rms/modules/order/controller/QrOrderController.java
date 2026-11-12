package com.rms.modules.order.controller;

import com.rms.modules.order.dto.OrderDtos.CreateRequest;
import com.rms.modules.order.dto.OrderDtos.Response;
import com.rms.modules.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/qr/orders")
public class QrOrderController {

    private final OrderService orders;

    public QrOrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(
            @RequestParam("token") String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateRequest request,
            Authentication authentication) {
        Long userId = authentication != null && authentication.getPrincipal() instanceof Long id ? id : null;
        return orders.createFromQr(token, request, idempotencyKey, userId);
    }

    @GetMapping("/{id}")
    public Response track(
            @RequestParam("token") String token,
            @PathVariable Long id) {
        return orders.trackByQr(token, id);
    }
}
