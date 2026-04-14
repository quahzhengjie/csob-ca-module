package com.csob.ca.shared.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Analyst-edited text. Advisory — carries no decisional weight.
 */
public record FinalSummaryDto(
        String editedText,
        String editedBy,
        Instant editedAt,
        String baseAiOutputHash
) {
    public FinalSummaryDto {
        Objects.requireNonNull(editedText, "editedText");
        Objects.requireNonNull(editedBy, "editedBy");
        Objects.requireNonNull(editedAt, "editedAt");
        Objects.requireNonNull(baseAiOutputHash, "baseAiOutputHash");
    }
}
