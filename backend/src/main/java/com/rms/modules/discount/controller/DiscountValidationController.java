package com.rms.modules.discount.controller;

import com.rms.modules.discount.dto.DiscountDtos.Quote;
import com.rms.modules.discount.dto.DiscountDtos.ValidateRequest;
import com.rms.modules.discount.service.DiscountService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/discounts")
public class DiscountValidationController {

    private final DiscountService discounts;

    public DiscountValidationController(DiscountService discounts) {
        this.discounts = discounts;
    }

    @PostMapping("/validate")
    public Quote validate(@Valid @RequestBody ValidateRequest request) {
        return discounts.validateCode(request.code(), request.subtotal());
    }
}
