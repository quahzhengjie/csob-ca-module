package com.csob.ca.tools.adapter;

import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.tool.ToolPayload;
import com.csob.ca.shared.enums.ToolId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Generic ToolInvoker that dispatches by {@link ToolId} to the matching
 * per-tool interface and wraps the returned {@link ToolPayload} in a
 * {@link ToolOutputDto} with fresh requestedAt / fetchedAt timestamps.
 *
 * Tools that are not yet implemented may be passed as {@code null};
 * invoking the corresponding ToolId throws
 * {@link UnsupportedOperationException} rather than NPE so the failure
 * mode is explicit in logs and the validation report.
 *
 * v1 source version is a placeholder ("stub-kyc-v1"); a later change will
 * feed this from the source adapters. Payload hashing is likewise a
 * placeholder pending the canonical-JSON hash utility.
 */
public final class DefaultToolInvoker implements ToolInvoker {

    private static final String DEFAULT_SOURCE_VERSION = "stub-kyc-v1";
    private static final String PLACEHOLDER_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private final PartyTool partyTool;                       // nullable
    private final DocumentMetadataTool documentMetadataTool; // nullable
    private final ScreeningTool screeningTool;               // nullable
    private final RelatedPartiesTool relatedPartiesTool;     // nullable
    private final Clock clock;

    public DefaultToolInvoker(PartyTool partyTool,
                              DocumentMetadataTool documentMetadataTool,
                              ScreeningTool screeningTool,
                              RelatedPartiesTool relatedPartiesTool) {
        this(partyTool, documentMetadataTool, screeningTool, relatedPartiesTool, Clock.systemUTC());
    }

    public DefaultToolInvoker(PartyTool partyTool,
                              DocumentMetadataTool documentMetadataTool,
                              ScreeningTool screeningTool,
                              RelatedPartiesTool relatedPartiesTool,
                              Clock clock) {
        this.partyTool            = partyTool;
        this.documentMetadataTool = documentMetadataTool;
        this.screeningTool        = screeningTool;
        this.relatedPartiesTool   = relatedPartiesTool;
        this.clock                = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ToolOutputDto invoke(String packId, ToolId toolId, String partyId) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(toolId, "toolId");
        Objects.requireNonNull(partyId, "partyId");

        Instant requestedAt = clock.instant();
        ToolPayload payload = dispatch(toolId, partyId);
        Instant fetchedAt = clock.instant();

        return new ToolOutputDto(
                buildToolOutputId(toolId, partyId, requestedAt),
                packId,
                toolId,
                requestedAt,
                fetchedAt,
                DEFAULT_SOURCE_VERSION,
                payload,
                PLACEHOLDER_HASH);
    }

    private ToolPayload dispatch(ToolId toolId, String partyId) {
        switch (toolId) {
            case PARTY:
                return require(partyTool, toolId).fetchPartyRecord(partyId);
            case DOCUMENT_METADATA:
                return require(documentMetadataTool, toolId).fetchDocumentMetadata(partyId);
            case SCREENING:
                return require(screeningTool, toolId).fetchScreeningResults(partyId);
            case RELATED_PARTIES:
                return require(relatedPartiesTool, toolId).fetchRelatedParties(partyId);
            default:
                throw new UnsupportedOperationException("Unknown ToolId: " + toolId);
        }
    }

    private static <T> T require(T tool, ToolId id) {
        if (tool == null) {
            throw new UnsupportedOperationException(
                    "No adapter registered for ToolId=" + id
                            + " (DefaultToolInvoker was constructed with null for this tool)");
        }
        return tool;
    }

    private static String buildToolOutputId(ToolId toolId, String partyId, Instant at) {
        return "to-" + toolId.name().toLowerCase() + "-" + partyId + "-" + at.toEpochMilli();
    }
}
