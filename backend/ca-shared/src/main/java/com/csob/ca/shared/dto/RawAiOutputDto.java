package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.ParseStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * The model's verbatim response. Always stored, even when malformed.
 * Advisory — cannot populate any system-of-record field.
 */
public record RawAiOutputDto(
        String packId,
        String modelId,
        String modelVersion,
        String promptVersion,
        Instant generatedAt,
        ParseStatus parseStatus,
        String rawJsonText,
        AiSummaryPayloadDto payload,
        String payloadHash
) {
    public RawAiOutputDto {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(promptVersion, "promptVersion");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(parseStatus, "parseStatus");
        Objects.requireNonNull(rawJsonText, "rawJsonText");
        Objects.requireNonNull(payloadHash, "payloadHash");
        if (parseStatus == ParseStatus.PARSED && payload == null) {
            throw new IllegalArgumentException("payload required when parseStatus = PARSED");
        }
        if (parseStatus == ParseStatus.MALFORMED && payload != null) {
            throw new IllegalArgumentException("payload must be null when parseStatus = MALFORMED");
        }
    }
}
