package com.csob.ca.shared.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record DocumentMetaDto(
        String documentId,
        String type,
        LocalDate issuedOn,
        LocalDate expiresOn,
        Instant uploadedAt,
        int pageCount,
        String mimeType,
        String hash
) {
    public DocumentMetaDto {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(uploadedAt, "uploadedAt");
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(hash, "hash");
    }
}
