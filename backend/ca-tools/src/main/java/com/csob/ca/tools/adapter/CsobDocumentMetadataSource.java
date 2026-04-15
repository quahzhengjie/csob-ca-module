package com.csob.ca.tools.adapter;

import com.csob.ca.shared.dto.DocumentMetaDto;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * CSOB-backed implementation of {@link DocumentMetadataSource}.
 *
 * Read-only. Metadata only — does NOT fetch BLOB / file content. Does NOT
 * perform OCR or extraction. The downstream {@link DocumentMetadataToolAdapter}
 * and pipeline are unchanged on swap; selection happens at the bean wiring
 * level via {@code ca.tools.documents.provider=CSOB}.
 *
 * Implementation status: <strong>STRUCTURE ONLY</strong>. The HTTP request /
 * response body and the CSOB-specific JSON ⇆ {@link DocumentMetaDto} mapping
 * are not yet implemented because the CSOB endpoint contract has not been
 * shared. This class declares its real dependencies (HttpClient, base URL,
 * timeout, retry budget, auth token, ObjectMapper) so the wiring is real,
 * fails loudly on misuse, and is one focused edit away from being live
 * once the API spec arrives.
 *
 * What needs to land when the CSOB API spec is available:
 *   1. Build the request URI from {@code baseUrl} + party-id query / header.
 *   2. POST/GET (per CSOB contract) with auth + timeout, retry on 5xx /
 *      transient IO up to {@code maxAttempts}, fail-fast on 4xx.
 *   3. Define an internal {@code CsobDocumentRecord} record matching the
 *      CSOB response shape.
 *   4. Map each {@code CsobDocumentRecord} → {@code DocumentMetaDto},
 *      dropping fields not represented on the DTO today (notably
 *      {@code fileName} and {@code size} per Phase-3 fields list — DTO
 *      extension is a separate, deliberate change).
 *   5. Return the assembled list in stable order.
 *
 * Until then, {@link #getDocumentsForParty(String)} throws
 * {@link UnsupportedOperationException} with an actionable message — the
 * application boots fine when {@code ca.tools.documents.provider=FILESYSTEM}
 * (the default), only fails when CSOB is selected without an implementation.
 */
public final class CsobDocumentMetadataSource implements DocumentMetadataSource {

    private static final Logger log = LoggerFactory.getLogger(CsobDocumentMetadataSource.class);

    private final HttpClient httpClient;
    private final URI baseUrl;
    private final Duration requestTimeout;
    private final int maxAttempts;
    /** Nullable / blank = no Authorization header sent. */
    private final String authToken;
    private final ObjectMapper objectMapper;

    public CsobDocumentMetadataSource(HttpClient httpClient,
                                      URI baseUrl,
                                      Duration requestTimeout,
                                      int maxAttempts,
                                      String authToken,
                                      ObjectMapper objectMapper) {
        this.httpClient     = Objects.requireNonNull(httpClient, "httpClient");
        this.baseUrl        = Objects.requireNonNull(baseUrl, "baseUrl");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("maxAttempts must be in [1, 3]; got " + maxAttempts);
        }
        this.maxAttempts  = maxAttempts;
        this.authToken    = authToken;                     // nullable
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        log.info("CsobDocumentMetadataSource initialised (baseUrl={}, timeout={}, maxAttempts={})",
                baseUrl, requestTimeout, maxAttempts);
    }

    @Override
    public List<DocumentMetaDto> getDocumentsForParty(String partyId) {
        Objects.requireNonNull(partyId, "partyId");
        // STRUCTURE ONLY — see class Javadoc.
        // When the CSOB endpoint contract lands, replace this body with:
        //   1. Build URL: e.g. baseUrl.resolve("/documents?partyId=" + URLEncoder.encode(partyId, UTF_8))
        //   2. HttpRequest with Accept: application/json + optional Bearer authToken
        //   3. httpClient.send with retry-on-5xx/IO up to maxAttempts
        //   4. Parse response body into List<CsobDocumentRecord> via objectMapper
        //   5. Map records → DocumentMetaDto in stable order; return List.copyOf(...)
        throw new UnsupportedOperationException(
                "CSOB endpoint contract not yet implemented. "
                        + "Provider was set to CSOB (baseUrl=" + baseUrl
                        + ") but the request/response shape is awaiting API spec from the CSOB "
                        + "integration team. To run locally now, set "
                        + "ca.tools.documents.provider=FILESYSTEM.");
    }

    /** For diagnostics / logging. */
    public URI baseUrl() {
        return baseUrl;
    }

    /** For diagnostics / logging. */
    public Duration requestTimeout() {
        return requestTimeout;
    }
}
