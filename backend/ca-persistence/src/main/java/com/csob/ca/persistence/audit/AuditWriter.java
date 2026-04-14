package com.csob.ca.persistence.audit;

import com.csob.ca.shared.dto.AuditEventDto;

/**
 * Append-only audit writer. Every write extends a per-pack hash chain so
 * tampering is detectable. Implementations MUST NOT update or delete rows.
 */
public interface AuditWriter {
    void recordEvent(AuditEventDto event);
}
