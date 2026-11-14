package com.rms.modules.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rms.common.GlobalExceptionHandler;
import com.rms.modules.settings.controller.SettingsController;
import com.rms.modules.settings.service.SettingsService;
import com.rms.modules.settings.service.SettingsService.View;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * REVIEW B11 — thuế suất là TỈ LỆ (0.08 = 8%), không phải phần trăm.
 * Gõ 8 thay vì 0.08 sẽ thành thuế 800% trên mọi đơn, nên phải bị từ chối.
 */
class SettingsControllerTest {

    private SettingsService settings;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        settings = mock(SettingsService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SettingsController(settings))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void taxRateTypedAsAPercentageIsRejected() throws Exception {
        mvc.perform(put("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taxRate": 8, "reservationDurationMinutes": 180}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("taxRate"));

        verify(settings, never()).update(any(), anyInt());
    }

    @Test
    void taxRateJustAboveOneIsRejected() throws Exception {
        mvc.perform(put("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taxRate": 1.0001, "reservationDurationMinutes": 180}
                                """))
                .andExpect(status().isBadRequest());

        verify(settings, never()).update(any(), anyInt());
    }

    @Test
    void ordinaryTaxRateIsAccepted() throws Exception {
        when(settings.update(new BigDecimal("0.08"), 180))
                .thenReturn(new View(new BigDecimal("0.0800"), 180, Instant.parse("2026-11-12T00:00:00Z")));

        mvc.perform(put("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"taxRate": 0.08, "reservationDurationMinutes": 180}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxRate").value(0.08));
    }

    @Test
    void zeroAndOneAreTheAllowedExtremes() throws Exception {
        for (String rate : new String[] {"0", "1"}) {
            mvc.perform(put("/api/v1/admin/settings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"taxRate\": " + rate + ", \"reservationDurationMinutes\": 180}"))
                    .andExpect(status().isOk());
        }
    }
}
