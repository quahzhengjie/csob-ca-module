package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.SectionHeading;

import java.util.List;
import java.util.Objects;

public record SectionDto(
        SectionHeading heading,
        List<SentenceDto> sentences
) {
    public SectionDto {
        Objects.requireNonNull(heading, "heading");
        sentences = List.copyOf(sentences);
        if (sentences.isEmpty()) {
            throw new IllegalArgumentException("section must have at least one sentence");
        }
    }
}
