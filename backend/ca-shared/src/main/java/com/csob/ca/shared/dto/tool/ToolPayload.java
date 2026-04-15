package com.csob.ca.shared.dto.tool;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Sealed hierarchy of tool result payloads. Each ToolId has exactly one
 * payload shape. The sealed interface prevents ad-hoc payload types and
 * enables exhaustive pattern matching by orchestration.
 *
 * Jackson polymorphic round-trip: {@link JsonTypeInfo.Id#DEDUCTION} lets
 * Jackson infer the concrete subtype from the property set in the JSON
 * (each subtype carries a unique top-level field name — {@code party},
 * {@code documents}, {@code results}, {@code relatedParties}). No type
 * tag is added to the wire format; serialise / deserialise round-trips
 * cleanly without contract churn.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
        @JsonSubTypes.Type(PartyToolPayload.class),
        @JsonSubTypes.Type(DocumentMetadataToolPayload.class),
        @JsonSubTypes.Type(ScreeningToolPayload.class),
        @JsonSubTypes.Type(RelatedPartiesToolPayload.class)
})
public sealed interface ToolPayload
        permits PartyToolPayload,
                DocumentMetadataToolPayload,
                ScreeningToolPayload,
                RelatedPartiesToolPayload {
}
