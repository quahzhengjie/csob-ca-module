package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.SourceType;

import java.util.Objects;

public record CitationDto(
        SourceType sourceType,
        String sourceId,
        String fieldPath
) {
    public CitationDto {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(fieldPath, "fieldPath");
    }
}
