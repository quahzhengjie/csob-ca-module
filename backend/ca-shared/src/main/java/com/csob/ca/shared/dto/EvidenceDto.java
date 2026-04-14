package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.SourceType;

import java.util.Objects;

public record EvidenceDto(
        SourceType sourceType,
        String sourceId,
        String fieldPath,
        String observedValue
) {
    public EvidenceDto {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(fieldPath, "fieldPath");
        Objects.requireNonNull(observedValue, "observedValue");
        if (sourceType == SourceType.CHECKLIST_FINDING) {
            throw new IllegalArgumentException(
                    "Evidence cannot cite another CHECKLIST_FINDING (would self-reference)");
        }
    }
}
