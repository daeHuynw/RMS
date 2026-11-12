package com.rms.modules.settings.service;

import com.rms.exception.ApiException;
import com.rms.modules.audit.service.AuditService;
import com.rms.modules.settings.entity.RestaurantSettings;
import com.rms.modules.settings.repository.RestaurantSettingsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final byte ROW_ID = 1;

    private final RestaurantSettingsRepository settings;
    private final AuditService audit;

    public SettingsService(RestaurantSettingsRepository settings, AuditService audit) {
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public View current() {
        return View.from(require());
    }

    @Transactional(readOnly = true)
    public BigDecimal taxRate() {
        return require().getTaxRate();
    }

    @Transactional(readOnly = true)
    public int reservationDurationMinutes() {
        return require().getReservationDurationMinutes();
    }

    @Transactional
    public View update(BigDecimal taxRate, int durationMinutes) {
        RestaurantSettings row = require();
        row.update(taxRate, durationMinutes);
        audit.record(
                "SETTINGS_UPDATED",
                "SETTINGS",
                "1",
                "tax=" + taxRate + ", duration=" + durationMinutes);
        return View.from(row);
    }

    private RestaurantSettings require() {
        return settings.findById(ROW_ID)
                .orElseThrow(() -> ApiException.notFound("Restaurant settings not found"));
    }

    public record View(BigDecimal taxRate, int reservationDurationMinutes, Instant updatedAt) {
        static View from(RestaurantSettings row) {
            return new View(row.getTaxRate(), row.getReservationDurationMinutes(), row.getUpdatedAt());
        }
    }
}
