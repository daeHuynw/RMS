package com.rms.modules.audit.service;

import com.rms.modules.audit.entity.AuditLog;
import com.rms.modules.audit.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public void record(String action, String targetType, String targetId, String detail) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long actor = auth != null && auth.getPrincipal() instanceof Long id ? id : null;
        String safeDetail = detail == null ? null : detail.substring(0, Math.min(detail.length(), 500));
        logs.save(new AuditLog(actor, action, targetType, targetId, safeDetail));
    }

    @Transactional(readOnly = true)
    public List<View> recent() {
        return logs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100)).stream()
                .map(View::from)
                .toList();
    }

    public record View(
            long id,
            Long actorId,
            String action,
            String targetType,
            String targetId,
            String detail,
            Instant createdAt) {

        static View from(AuditLog log) {
            return new View(
                    log.getId(),
                    log.getActorId(),
                    log.getAction(),
                    log.getTargetType(),
                    log.getTargetId(),
                    log.getDetail(),
                    log.getCreatedAt());
        }
    }
}
