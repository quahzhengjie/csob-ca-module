package com.csob.ca.shared.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The parsed, schema-valid structure the AI is permitted to return.
 * Advisory only — never authoritative.
 */
public record AiSummaryPayloadDto(
        String packId,
        String checklistVersion,
        String modelId,
        String modelVersion,
        Instant generatedAt,
        List<SectionDto> sections
) {
    public AiSummaryPayloadDto {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(checklistVersion, "checklistVersion");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(generatedAt, "generatedAt");
        sections = List.copyOf(sections);
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("sections must be non-empty");
        }
    }
}
