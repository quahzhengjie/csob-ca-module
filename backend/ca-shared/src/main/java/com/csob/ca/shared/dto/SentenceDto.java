package com.csob.ca.shared.dto;

import java.util.List;
import java.util.Objects;

public record SentenceDto(
        String text,
        List<CitationDto> citations,
        List<FactMentionDto> factMentions
) {
    public SentenceDto {
        Objects.requireNonNull(text, "text");
        citations = List.copyOf(citations);
        factMentions = List.copyOf(factMentions);
        if (citations.isEmpty()) {
            throw new IllegalArgumentException(
                    "sentence must have at least one citation (uncited sentences are rejected)");
        }
    }
}
