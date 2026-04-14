package com.csob.ca.shared.dto.tool;

import com.csob.ca.shared.dto.DocumentMetaDto;

import java.util.List;

public record DocumentMetadataToolPayload(List<DocumentMetaDto> documents) implements ToolPayload {
    public DocumentMetadataToolPayload {
        documents = List.copyOf(documents);
    }
}
