package com.csob.ca.ai.provider;

import com.csob.ca.ai.ModelClient;
import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.ai.request.AiResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Deterministic stub for local dev, smoke tests, and CI. Returns a canned,
 * schema-valid AI response that exercises the v1 validation chain end-to-end.
 *
 * Two shapes of use:
 *   - no-arg constructor   : returns {@link #DEFAULT_CANNED_JSON}. Used by
 *                            the default wiring in OrchestrationConfig so
 *                            the application can boot without a real model.
 *   - (String) constructor : returns the supplied canned JSON verbatim.
 *                            Used by smoke tests to drive intentional PASS
 *                            / FAIL scenarios through the pipeline.
 *
 * The canned JSON embeds a FIXED packId ("pack-test-0001") and
 * checklistVersion ("v1.0"); smoke fixtures must use the same values for
 * PACK_BINDING to pass. Never used in staging or prod profiles.
 */
public final class StubModelClient implements ModelClient {

    /**
     * Default canned response. Schema-valid under
     * /schemas/ai-output.schema.json. All values chosen to ground cleanly
     * against the smoke runner's mock fixtures (doc-001, R-DOC-EXPIRED).
     */
    public static final String DEFAULT_CANNED_JSON = """
            {
              "packId": "pack-test-0001",
              "checklistVersion": "v1.0",
              "modelId": "stub-model",
              "modelVersion": "stub-1.0",
              "generatedAt": "2026-04-15T00:00:00Z",
              "sections": [
                {
                  "heading": "DOCUMENTS",
                  "sentences": [
                    {
                      "text": "Finding R-DOC-EXPIRED: document doc-001 expires on 2026-04-01.",
                      "citations": [
                        { "sourceType": "CHECKLIST_FINDING","sourceId": "R-DOC-EXPIRED",  "fieldPath": "ruleId"     },
                        { "sourceType": "DOCUMENT_META",    "sourceId": "doc-001",        "fieldPath": "documentId" },
                        { "sourceType": "DOCUMENT_META",    "sourceId": "doc-001",        "fieldPath": "expiresOn"  }
                      ],
                      "factMentions": [
                        { "value": "R-DOC-EXPIRED", "kind": "IDENTIFIER",
                          "citation": { "sourceType": "CHECKLIST_FINDING", "sourceId": "R-DOC-EXPIRED", "fieldPath": "ruleId" } },
                        { "value": "doc-001", "kind": "IDENTIFIER",
                          "citation": { "sourceType": "DOCUMENT_META", "sourceId": "doc-001", "fieldPath": "documentId" } },
                        { "value": "2026-04-01", "kind": "DATE",
                          "citation": { "sourceType": "DOCUMENT_META", "sourceId": "doc-001", "fieldPath": "expiresOn" } }
                      ]
                    }
                  ]
                }
              ]
            }
            """;

    private static final String STUB_MODEL_ID      = "stub-model";
    private static final String STUB_MODEL_VERSION = "stub-1.0";

    private final String cannedJson;
    private final Clock clock;

    public StubModelClient() {
        this(DEFAULT_CANNED_JSON, Clock.systemUTC());
    }

    public StubModelClient(String cannedJson) {
        this(cannedJson, Clock.systemUTC());
    }

    public StubModelClient(String cannedJson, Clock clock) {
        this.cannedJson = Objects.requireNonNull(cannedJson, "cannedJson");
        this.clock      = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AiResponse invoke(AiRequest request) {
        Objects.requireNonNull(request, "request");
        return new AiResponse(
                request.packId(),
                STUB_MODEL_ID,
                STUB_MODEL_VERSION,
                Instant.now(clock),
                cannedJson);
    }
}
