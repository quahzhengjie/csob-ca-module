package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.ValidationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic gate verdict over a RawAiOutput. System of record.
 * status = VALID iff every check passed; otherwise REJECTED.
 */
public record ValidationReportDto(
        String packId,
        Instant validatedAt,
        String aiOutputHash,
        ValidationStatus status,
        List<ValidationCheckResultDto> checks
) {
    public ValidationReportDto {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(validatedAt, "validatedAt");
        Objects.requireNonNull(aiOutputHash, "aiOutputHash");
        Objects.requireNonNull(status, "status");
        checks = List.copyOf(checks);
        if (checks.isEmpty()) {
            throw new IllegalArgumentException("checks must be non-empty");
        }
    }
}
