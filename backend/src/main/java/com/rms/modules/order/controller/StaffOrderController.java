package com.rms.modules.order.controller;

import com.rms.modules.order.dto.OrderDtos.CancelRequest;
import com.rms.modules.order.dto.OrderDtos.CreateRequest;
import com.rms.modules.order.dto.OrderDtos.Response;
import com.rms.modules.order.dto.OrderDtos.StaffCreateRequest;
import com.rms.modules.order.dto.OrderDtos.StatusRequest;
import com.rms.modules.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/staff/orders")
public class StaffOrderController {

    private final OrderService orders;

    public StaffOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public List<Response> list() {
        return orders.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody StaffCreateRequest request,
            Authentication authentication) {
        CreateRequest body = new CreateRequest(request.items(), request.customerNote(), request.discountCode());
        return orders.createFromStaff(
                request.tableSessionId(),
                body,
                idempotencyKey,
                (Long) authentication.getPrincipal());
    }

    @PatchMapping("/{id}/status")
    public Response status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return orders.transition(id, request.status());
    }

    @PostMapping("/{id}/cancel")
    public Response cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return orders.cancel(id, request.reason());
    }
}
