package com.csob.ca.tools.adapter;

import com.csob.ca.shared.dto.DocumentMetaDto;
import com.csob.ca.shared.dto.tool.DocumentMetadataToolPayload;

import java.util.List;
import java.util.Objects;

/**
 * First real tool adapter. Read-only. Deterministic. Delegates to a
 * {@link DocumentMetadataSource} for the actual fetch — the source is
 * swappable (filesystem today, CSOB tomorrow) without any changes to this
 * adapter, the ToolInvoker, or the pipeline.
 *
 * Responsibilities (intentionally narrow):
 *   - invoke the configured source
 *   - wrap the returned {@code List<DocumentMetaDto>} in the typed
 *     {@link DocumentMetadataToolPayload} sealed variant
 *
 * Forbidden here:
 *   - any business logic (rule evaluation, scoring, filtering)
 *   - any AI call or external network call
 *   - any caching, retry, or mutation
 */
public final class DocumentMetadataToolAdapter implements DocumentMetadataTool {

    private final DocumentMetadataSource source;

    public DocumentMetadataToolAdapter(DocumentMetadataSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public DocumentMetadataToolPayload fetchDocumentMetadata(String partyId) {
        Objects.requireNonNull(partyId, "partyId");
        List<DocumentMetaDto> docs = source.getDocumentsForParty(partyId);
        return new DocumentMetadataToolPayload(docs == null ? List.of() : docs);
    }
}
