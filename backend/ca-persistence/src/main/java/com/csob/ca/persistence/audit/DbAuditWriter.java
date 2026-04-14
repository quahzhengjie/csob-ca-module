package com.csob.ca.persistence.audit;

import com.csob.ca.shared.dto.AuditEventDto;

public final class DbAuditWriter implements AuditWriter {

    private final AuditLogJpaRepository auditLogRepository;

    public DbAuditWriter(AuditLogJpaRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void recordEvent(AuditEventDto event) {
        throw new UnsupportedOperationException(
                "Skeleton — read the prev hash for event.packId(), compute new hash " +
                "over (prevHash, event), insert a new AuditLogEntity.");
    }
}
