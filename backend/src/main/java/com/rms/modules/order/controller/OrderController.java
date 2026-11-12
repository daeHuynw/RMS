package com.rms.modules.order.controller;

import com.rms.modules.order.dto.OrderDtos.CreateRequest;
import com.rms.modules.order.dto.OrderDtos.ItemChange;
import com.rms.modules.order.dto.OrderDtos.ItemRequest;
import com.rms.modules.order.dto.OrderDtos.OrderContext;
import com.rms.modules.order.dto.OrderDtos.Response;
import com.rms.modules.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/context")
    public OrderContext context(Authentication authentication) {
        return orders.orderingContext((Long) authentication.getPrincipal());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateRequest request,
            Authentication authentication) {
        return orders.createFromCustomer(request, idempotencyKey, (Long) authentication.getPrincipal());
    }

    @GetMapping("/my")
    public List<Response> mine(Authentication authentication) {
        return orders.mine((Long) authentication.getPrincipal());
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id, Authentication authentication) {
        return orders.get(id, (Long) authentication.getPrincipal(), isStaff(authentication));
    }

    @PostMapping("/{id}/items")
    public Response addItem(
            @PathVariable Long id,
            @Valid @RequestBody ItemRequest request,
            Authentication authentication) {
        return orders.addItem(id, request, (Long) authentication.getPrincipal(), isStaff(authentication));
    }

    @PatchMapping("/{id}/items/{itemId}")
    public Response updateItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody ItemChange change,
            Authentication authentication) {
        return orders.updateItem(
                id, itemId, change, (Long) authentication.getPrincipal(), isStaff(authentication));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public Response removeItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            Authentication authentication) {
        return orders.removeItem(id, itemId, (Long) authentication.getPrincipal(), isStaff(authentication));
    }

    private static boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_STAFF") || a.equals("ROLE_ADMIN"));
    }
}
