package com.csob.ca.shared.dto;

import java.time.Instant;
import java.util.Objects;

public record AuditEventDto(
        String packId,
        String eventType,
        String detailsJson,
        Instant occurredAt
) {
    public AuditEventDto {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(detailsJson, "detailsJson");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
