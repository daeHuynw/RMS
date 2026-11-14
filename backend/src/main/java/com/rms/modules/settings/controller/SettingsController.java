package com.rms.modules.settings.controller;

import com.rms.modules.settings.service.SettingsService;
import com.rms.modules.settings.service.SettingsService.View;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/settings")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public View current() {
        return settings.current();
    }

    @PutMapping
    public View update(@Valid @RequestBody Update request) {
        return settings.update(request.taxRate(), request.reservationDurationMinutes());
    }

    public record Update(
            // Tỉ lệ, không phải phần trăm: 0.08 = 8%. Chặn trên 1 để gõ nhầm "8" không thành thuế 800%.
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal taxRate,
            @Min(30) @Max(240) int reservationDurationMinutes) {
    }
}
