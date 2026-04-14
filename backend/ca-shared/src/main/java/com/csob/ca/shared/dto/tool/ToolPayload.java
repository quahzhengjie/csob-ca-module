package com.csob.ca.shared.dto.tool;

/**
 * Sealed hierarchy of tool result payloads. Each ToolId has exactly one
 * payload shape. The sealed interface prevents ad-hoc payload types and
 * enables exhaustive pattern matching by orchestration.
 */
public sealed interface ToolPayload
        permits PartyToolPayload,
                DocumentMetadataToolPayload,
                ScreeningToolPayload,
                RelatedPartiesToolPayload {
}
