package com.csob.ca.tools.adapter;

import com.csob.ca.shared.dto.DocumentMetaDto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Local-filesystem implementation of {@link DocumentMetadataSource}.
 *
 * Layout:
 *   {baseDir}/{partyId}.json
 *
 * Each JSON file is an array of DocumentMetaDto objects, e.g.:
 *   [
 *     {
 *       "documentId":  "doc-001",
 *       "type":        "PASSPORT",
 *       "issuedOn":    "2020-01-01",
 *       "expiresOn":   "2026-04-01",
 *       "uploadedAt":  "2026-01-01T00:00:00Z",
 *       "pageCount":   10,
 *       "mimeType":    "application/pdf",
 *       "hash":        "0000..."
 *     }
 *   ]
 *
 * Behaviour:
 *   - missing file            → empty list (party-has-no-docs, not an error)
 *   - malformed JSON          → IllegalStateException (wrapping the cause)
 *   - IO error while reading  → IllegalStateException
 *
 * Read-only, deterministic for equal filesystem state. No business logic,
 * no caching, no watchers.
 *
 * CSOB integration note: when a CsobDocumentMetadataSource is added, it
 * will implement the same {@link DocumentMetadataSource} interface; nothing
 * else in the codebase needs to change.
 */
public final class FilesystemDocumentMetadataSource implements DocumentMetadataSource {

    private static final Logger log = LoggerFactory.getLogger(FilesystemDocumentMetadataSource.class);
    private static final TypeReference<List<DocumentMetaDto>> LIST_OF_DOCS =
            new TypeReference<>() {};

    private final Path baseDir;
    private final ObjectMapper objectMapper;

    public FilesystemDocumentMetadataSource(Path baseDir, ObjectMapper objectMapper) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir").toAbsolutePath().normalize();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public List<DocumentMetaDto> getDocumentsForParty(String partyId) {
        Objects.requireNonNull(partyId, "partyId");
        Path file = resolveForParty(partyId);
        if (!Files.isRegularFile(file)) {
            log.debug("No document fixture at {} for partyId={}", file, partyId);
            return List.of();
        }
        try (var in = Files.newInputStream(file)) {
            List<DocumentMetaDto> docs = objectMapper.readValue(in, LIST_OF_DOCS);
            return (docs == null) ? List.of() : List.copyOf(docs);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read document metadata for partyId=" + partyId + " from " + file, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Failed to parse document metadata for partyId=" + partyId + " from " + file, e);
        }
    }

    private Path resolveForParty(String partyId) {
        // Guard: do not permit "../.." traversal by callers. partyId must be a simple identifier.
        if (partyId.isEmpty()
                || partyId.contains("/")
                || partyId.contains("\\")
                || partyId.contains("..")) {
            throw new IllegalArgumentException("partyId must be a simple identifier, was: " + partyId);
        }
        return baseDir.resolve(partyId + ".json").normalize();
    }

    /** For diagnostics / logging. */
    public Path baseDir() {
        return baseDir;
    }
}
