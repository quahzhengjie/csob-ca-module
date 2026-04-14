package com.csob.ca.shared.dto;

import com.csob.ca.shared.dto.tool.ToolPayload;
import com.csob.ca.shared.enums.ToolId;

import java.time.Instant;
import java.util.Objects;

/**
 * One row per tool invocation in the pipeline. Frozen and hashed before
 * downstream layers (checklist, AI, validation) read it.
 * System of record — authoritative evidence.
 */
public record ToolOutputDto(
        String toolOutputId,
        String packId,
        ToolId toolId,
        Instant requestedAt,
        Instant fetchedAt,
        String sourceVersion,
        ToolPayload payload,
        String payloadHash
) {
    public ToolOutputDto {
        Objects.requireNonNull(toolOutputId, "toolOutputId");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(toolId, "toolId");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        Objects.requireNonNull(sourceVersion, "sourceVersion");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(payloadHash, "payloadHash");
    }
}
