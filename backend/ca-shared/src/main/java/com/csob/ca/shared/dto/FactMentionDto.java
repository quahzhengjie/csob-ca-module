package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.FactKind;

import java.util.Objects;

public record FactMentionDto(
        String value,
        FactKind kind,
        CitationDto citation
) {
    public FactMentionDto {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(citation, "citation");
    }
}
