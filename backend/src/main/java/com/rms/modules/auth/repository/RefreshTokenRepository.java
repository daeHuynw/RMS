package com.rms.modules.auth.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.rms.modules.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Mọi refresh token còn hiệu lực của một tài khoản (mỗi phiên đăng nhập một token). */
    List<RefreshToken> findByUser_IdAndRevokedAtIsNull(Long userId);

    /** Xoá token đã thu hồi trước {@code cutoff} hoặc đã hết hạn trước {@code cutoff}. */
    @Modifying
    @Query("delete from RefreshToken t where t.revokedAt < :cutoff or t.expiresAt < :cutoff")
    int deleteRevokedOrExpiredBefore(@Param("cutoff") Instant cutoff);
}
