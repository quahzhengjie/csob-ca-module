package com.csob.ca.persistence.audit;

import com.csob.ca.persistence.entity.AuditLogEntity;
import com.csob.ca.shared.dto.AuditEventDto;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * JPA-backed implementation of {@link AuditReader}. Read-only.
 *
 * Returns events in append order. Strips the per-row hash chain values
 * from the result — those remain inside the persistence layer (the chain
 * itself is verifiable by re-deriving over the JSON contents on demand,
 * not exposed through this read API).
 */
public class DbAuditReader implements AuditReader {
    // non-final: Spring CGLIB subclasses this type to proxy @Transactional methods.

    private final AuditLogJpaRepository auditLogRepository;

    public DbAuditReader(AuditLogJpaRepository auditLogRepository) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEventDto> findEventsByPackId(String packId) {
        Objects.requireNonNull(packId, "packId");
        List<AuditLogEntity> rows =
                auditLogRepository.findByPackIdOrderByOccurredAtAscEventIdAsc(packId);
        return rows.stream()
                .map(r -> new AuditEventDto(
                        r.getPackId(),
                        r.getEventType(),
                        r.getEventJson(),
                        r.getOccurredAt()))
                .toList();
    }
}
