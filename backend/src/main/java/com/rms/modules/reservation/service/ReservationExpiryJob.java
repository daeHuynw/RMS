package com.rms.modules.reservation.service;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tác vụ định kỳ: nhả chỗ của các lượt đặt đã quá hạn (REVIEW B10).
 * Luật nằm trong ReservationService.expireStale; class này chỉ lo "bao lâu chạy một lần".
 */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final ReservationService reservations;
    private final Duration noShowGrace;

    public ReservationExpiryJob(
            ReservationService reservations,
            @Value("${rms.reservation.no-show-grace-minutes:30}") long noShowGraceMinutes) {
        this.reservations = reservations;
        this.noShowGrace = Duration.ofMinutes(noShowGraceMinutes);
    }

    /** fixedDelay: lần sau bắt đầu sau khi lần trước XONG một khoảng, nên không bao giờ chạy chồng nhau. */
    @Scheduled(
            initialDelayString = "${rms.reservation.sweep-interval-ms:60000}",
            fixedDelayString = "${rms.reservation.sweep-interval-ms:60000}")
    public void sweep() {
        int changed = reservations.expireStale(Instant.now(), noShowGrace);
        if (changed > 0) {
            log.info("Reservation sweep closed {} stale reservation(s)", changed);
        }
    }
}
