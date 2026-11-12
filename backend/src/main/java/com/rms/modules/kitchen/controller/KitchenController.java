package com.rms.modules.kitchen.controller;

import com.rms.modules.kitchen.dto.KitchenDtos.StatusRequest;
import com.rms.modules.kitchen.dto.KitchenDtos.Ticket;
import com.rms.modules.kitchen.service.KitchenService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/kitchen")
public class KitchenController {

    private final KitchenService kitchen;

    public KitchenController(KitchenService kitchen) {
        this.kitchen = kitchen;
    }

    @GetMapping("/orders")
    public List<Ticket> queue() {
        return kitchen.queue();
    }

    @GetMapping("/orders/{id}")
    public Ticket get(@PathVariable Long id) {
        return kitchen.get(id);
    }

    @PatchMapping("/order-items/{itemId}/status")
    public Ticket updateItem(@PathVariable Long itemId, @Valid @RequestBody StatusRequest request) {
        return kitchen.updateItemStatus(itemId, request.status());
    }
}
