package com.csob.ca.tools.adapter;

import com.csob.ca.shared.dto.tool.DocumentMetadataToolPayload;

/**
 * Metadata only — no OCR, no raw text extraction in v1.
 */
public interface DocumentMetadataTool {
    DocumentMetadataToolPayload fetchDocumentMetadata(String partyId);
}
