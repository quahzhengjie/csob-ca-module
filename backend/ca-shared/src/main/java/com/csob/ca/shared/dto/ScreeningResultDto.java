package com.csob.ca.shared.dto;

import java.time.Instant;
import java.util.Objects;

public record ScreeningResultDto(
        String resultId,
        String providerId,
        String list,
        int matchStrength,
        int hitsCount,
        Instant retrievedAt
) {
    public ScreeningResultDto {
        Objects.requireNonNull(resultId, "resultId");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(retrievedAt, "retrievedAt");
    }
}
