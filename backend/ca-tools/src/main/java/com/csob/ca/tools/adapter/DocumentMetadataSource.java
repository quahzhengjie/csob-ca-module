package com.csob.ca.tools.adapter;

import com.csob.ca.shared.dto.DocumentMetaDto;

import java.util.List;

/**
 * Port: the concept of "where document metadata comes from", without
 * tying the adapter layer to any particular backing store.
 *
 * Current implementations:
 *   - {@link FilesystemDocumentMetadataSource} — reads per-party JSON files
 *     from a local directory. Used today for smoke and local dev.
 *
 * Planned / future:
 *   - CsobDocumentMetadataSource — calls the CSOB document-metadata service
 *     directly. Will implement this same interface so
 *     {@link DocumentMetadataToolAdapter} and every caller upstream of it
 *     (orchestration, smoke runner, prod pipeline) are unchanged on swap.
 *
 * Contract:
 *   - read-only, deterministic for equal inputs and equal backing state
 *   - never mutates upstream data
 *   - returns an empty list (never null) for unknown / party-with-no-docs
 *   - failures in the backing store are signalled by runtime exception
 *     (callers decide whether to retry or fail the pipeline step)
 */
public interface DocumentMetadataSource {

    /**
     * @param partyId the party to load documents for
     * @return list of document metadata for the party, in a stable order;
     *         empty list if the party has no documents; never null
     */
    List<DocumentMetaDto> getDocumentsForParty(String partyId);
}
