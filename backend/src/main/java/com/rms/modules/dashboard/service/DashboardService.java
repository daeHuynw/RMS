package com.rms.modules.dashboard.service;

import com.rms.exception.ApiException;
import com.rms.modules.dashboard.repository.DashboardRepository;
import com.rms.modules.table.entity.TableStatus;
import com.rms.modules.table.repository.DiningTableRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardRepository reports;
    private final DiningTableRepository tables;

    public DashboardService(DashboardRepository reports, DiningTableRepository tables) {
        this.reports = reports;
        this.tables = tables;
    }

    public Overview overview(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        return new Overview(
                reports.countOrders(range.start, range.end),
                reports.countCustomers(range.start, range.end),
                reports.revenue(range.start, range.end),
                tables.countByStatus(TableStatus.OCCUPIED),
                tables.countByStatus(TableStatus.RESERVED),
                tables.countByStatus(TableStatus.AVAILABLE));
    }

    public Revenue revenue(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        return new Revenue(reports.revenue(range.start, range.end), reports.revenueByDay(range.start, range.end));
    }

    public OrderStats orders(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        var rows = reports.ordersByStatus(range.start, range.end);
        long total = rows.stream().mapToLong(row -> row.getTotal() == null ? 0 : row.getTotal()).sum();
        long cancelled = rows.stream()
                .filter(row -> "CANCELLED".equals(row.getStatus()))
                .mapToLong(DashboardRepository.StatusRow::getTotal)
                .sum();
        BigDecimal rate = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(cancelled).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        return new OrderStats(total, cancelled, rate, rows);
    }

    public List<DashboardRepository.HourRow> peakHours(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        return reports.peakHours(range.start, range.end);
    }

    public List<DashboardRepository.TopSellingRow> topSelling(LocalDate from, LocalDate to, int limit) {
        Range range = range(from, to);
        if (limit < 1 || limit > 100) {
            throw ApiException.badRequest("INVALID_LIMIT", "limit must be between 1 and 100");
        }
        return reports.topSelling(range.start, range.end, PageRequest.of(0, limit));
    }

    private static Range range(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_RANGE", "from/to required and to must not precede from");
        }
        return new Range(
                from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    private record Range(Instant start, Instant end) {
    }

    public record Overview(
            long orderCount,
            long customerCount,
            BigDecimal revenue,
            long occupiedTables,
            long reservedTables,
            long availableTables) {
    }

    public record Revenue(BigDecimal total, List<DashboardRepository.RevenueRow> daily) {
    }

    public record OrderStats(
            long total,
            long cancelled,
            BigDecimal cancellationRate,
            List<DashboardRepository.StatusRow> byStatus) {
    }
}
