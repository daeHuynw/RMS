package com.rms.modules.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rms.modules.dashboard.repository.DashboardRepository;
import com.rms.modules.dashboard.service.DashboardService;
import com.rms.modules.table.repository.DiningTableRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Admin chọn ngày theo lịch của nhà hàng (giờ VN), còn DB lưu UTC.
 * "Ngày 12/11" phải là 00:00–24:00 giờ VN = 17:00Z hôm trước → 17:00Z hôm đó.
 */
class DashboardServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);
    private static final Instant VN_DAY_START = Instant.parse("2026-11-11T17:00:00Z");
    private static final Instant VN_DAY_END = Instant.parse("2026-11-12T17:00:00Z");

    private DashboardRepository reports;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        reports = mock(DashboardRepository.class);
        service = new DashboardService(
                reports, mock(DiningTableRepository.class), ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test
    void overviewCutsTheDayAtRestaurantMidnight() {
        when(reports.revenue(any(), any())).thenReturn(BigDecimal.ZERO);

        service.overview(DAY, DAY);

        verify(reports).countOrders(VN_DAY_START, VN_DAY_END);
        verify(reports).countCustomers(VN_DAY_START, VN_DAY_END);
        verify(reports).revenue(VN_DAY_START, VN_DAY_END);
    }

    @Test
    void peakHoursAreBucketedInRestaurantTime() {
        when(reports.peakHours(any(), any(), any())).thenReturn(List.of());

        assertThat(service.peakHours(DAY, DAY)).isEmpty();

        verify(reports).peakHours(VN_DAY_START, VN_DAY_END, "+07:00");
    }

    @Test
    void dailyRevenueIsBucketedInRestaurantTime() {
        when(reports.revenue(any(), any())).thenReturn(BigDecimal.ZERO);
        when(reports.revenueByDay(any(), any(), any())).thenReturn(List.of());

        service.revenue(DAY, DAY);

        verify(reports).revenueByDay(VN_DAY_START, VN_DAY_END, "+07:00");
    }

    @Test
    void utcRestaurantUsesPlainZeroOffset() {
        var utcService = new DashboardService(reports, mock(DiningTableRepository.class), ZoneId.of("UTC"));
        when(reports.peakHours(any(), any(), any())).thenReturn(List.of());

        utcService.peakHours(DAY, DAY);

        // MySQL CONVERT_TZ không hiểu "Z" — phải là "+00:00".
        verify(reports).peakHours(
                Instant.parse("2026-11-12T00:00:00Z"), Instant.parse("2026-11-13T00:00:00Z"), "+00:00");
    }
}
