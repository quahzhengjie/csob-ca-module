package com.csob.ca.ai.request;

import java.time.Instant;
import java.util.Objects;

/**
 * Verbatim response from the model. Raw text is always returned —
 * parsing and validation is the responsibility of ca-validation, not here.
 */
public record AiResponse(
        String packId,
        String modelId,
        String modelVersion,
        Instant generatedAt,
        String rawJsonText
) {
    public AiResponse {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(rawJsonText, "rawJsonText");
    }
}
