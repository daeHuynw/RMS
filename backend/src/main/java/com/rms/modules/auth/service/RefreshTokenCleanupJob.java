package com.rms.modules.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tác vụ định kỳ: xoá refresh token đã thu hồi hoặc hết hạn quá {@code retentionDays} ngày.
 * Token như vậy đằng nào cũng không refresh được; giữ lại chỉ làm bảng phình ra.
 * Việc xoá nằm trong AuthService (@Transactional); class này chỉ lo "khi nào" và "mốc nào".
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final AuthService auth;
    private final Duration retention;
    private final Clock clock;

    @Autowired
    public RefreshTokenCleanupJob(
            AuthService auth,
            @Value("${rms.security.refresh-token-retention-days:7}") long retentionDays) {
        this(auth, retentionDays, Clock.systemUTC());
    }

    public RefreshTokenCleanupJob(AuthService auth, long retentionDays, Clock clock) {
        this.auth = auth;
        this.retention = Duration.ofDays(retentionDays);
        this.clock = clock;
    }

    @Scheduled(
            cron = "${rms.security.refresh-token-cleanup-cron:0 0 3 * * *}",
            zone = "${rms.time-zone:Asia/Ho_Chi_Minh}")
    public int purge() {
        Instant cutoff = clock.instant().minus(retention);
        int deleted = auth.purgeRefreshTokensBefore(cutoff);
        if (deleted > 0) {
            log.info("Refresh token cleanup deleted {} token(s) revoked/expired before {}", deleted, cutoff);
        }
        return deleted;
    }
}
