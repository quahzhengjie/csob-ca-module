package com.csob.ca.ai.provider;

import com.csob.ca.ai.ModelClient;
import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.ai.request.AiResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, context-aware stub for local dev, smoke tests, and CI.
 * Returns a schema-valid AI response that exercises the v1 validation
 * chain end-to-end without calling a real model.
 *
 * Templating:
 *   The two built-in scenarios (PASS / FAIL) are JSON templates with
 *   {@code {{packId}}} and {@code {{checklistVersion}}} placeholders.
 *   {@link #invoke(AiRequest)} substitutes those values from the incoming
 *   request, so the response always agrees with the pipeline's PACK_BINDING
 *   contract regardless of the runtime-generated packId.
 *
 *   - {{packId}}            ← request.packId()
 *   - {{checklistVersion}}  ← extracted from request.userPrompt() via
 *                              the prompt-template marker
 *                              "Checklist version: <value>".
 *                              Falls back to "v1.0" if the marker is absent
 *                              (deterministic, never fails).
 *
 * Two shapes of construction:
 *   - no-arg / Scenario.PASS  → {@link #PASS_TEMPLATE} — every fact grounds.
 *   - Scenario.FAIL           → {@link #FAIL_TEMPLATE} — single intentional
 *                                FACT_NOT_GROUNDED at
 *                                /sections/0/sentences/0/factMentions/2.
 *   - (String) constructor    → arbitrary template; {@code {{packId}}} and
 *                                {@code {{checklistVersion}}} still substituted.
 *
 * Determinism: same AiRequest → byte-identical AiResponse.rawJsonText
 * (modulo the AiResponse.generatedAt timestamp, which is the wall-clock
 * read at invoke time).
 *
 * Never used in staging or prod profiles.
 */
public final class StubModelClient implements ModelClient {

    public enum Scenario { PASS, FAIL }

    /** PASS scenario: all factMentions ground cleanly against fixture data. */
    public static final String PASS_TEMPLATE = """
            {
              "packId": "{{packId}}",
              "checklistVersion": "{{checklistVersion}}",
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

    /**
     * FAIL scenario: identical to PASS_TEMPLATE except the DATE factMention
     * value is intentionally wrong (2099-12-31 vs source 2026-04-01) so
     * FACT_GROUNDING raises one FACT_NOT_GROUNDED failure at
     * /sections/0/sentences/0/factMentions/2.
     */
    public static final String FAIL_TEMPLATE = """
            {
              "packId": "{{packId}}",
              "checklistVersion": "{{checklistVersion}}",
              "modelId": "stub-model",
              "modelVersion": "stub-1.0",
              "generatedAt": "2026-04-15T00:00:00Z",
              "sections": [
                {
                  "heading": "DOCUMENTS",
                  "sentences": [
                    {
                      "text": "Finding R-DOC-EXPIRED: document doc-001 expires on 2099-12-31.",
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
                        { "value": "2099-12-31", "kind": "DATE",
                          "citation": { "sourceType": "DOCUMENT_META", "sourceId": "doc-001", "fieldPath": "expiresOn" } }
                      ]
                    }
                  ]
                }
              ]
            }
            """;

    /** Back-compat alias — earlier code referenced this constant. */
    public static final String DEFAULT_CANNED_JSON = PASS_TEMPLATE;

    private static final String PH_PACK_ID            = "{{packId}}";
    private static final String PH_CHECKLIST_VERSION  = "{{checklistVersion}}";
    private static final String FALLBACK_CHECKLIST_VERSION = "v1.0";

    /**
     * Matches the line emitted by user_template.md after substitution:
     *   "Checklist version: v1.0"
     * Captures the version token (anything non-whitespace).
     */
    private static final Pattern CHECKLIST_VERSION_MARKER =
            Pattern.compile("Checklist version:\\s*(\\S+)");

    private static final String STUB_MODEL_ID      = "stub-model";
    private static final String STUB_MODEL_VERSION = "stub-1.0";

    private final String template;
    private final Clock clock;

    public StubModelClient() {
        this(Scenario.PASS);
    }

    public StubModelClient(Scenario scenario) {
        this(scenarioTemplate(scenario), Clock.systemUTC());
    }

    public StubModelClient(String template) {
        this(template, Clock.systemUTC());
    }

    public StubModelClient(String template, Clock clock) {
        this.template = Objects.requireNonNull(template, "template");
        this.clock    = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AiResponse invoke(AiRequest request) {
        Objects.requireNonNull(request, "request");

        String checklistVersion = extractChecklistVersion(request.userPrompt());
        String body = template
                .replace(PH_PACK_ID,           request.packId())
                .replace(PH_CHECKLIST_VERSION, checklistVersion);

        return new AiResponse(
                request.packId(),
                STUB_MODEL_ID,
                STUB_MODEL_VERSION,
                Instant.now(clock),
                body);
    }

    // ---- helpers ----

    private static String scenarioTemplate(Scenario scenario) {
        Objects.requireNonNull(scenario, "scenario");
        return (scenario == Scenario.FAIL) ? FAIL_TEMPLATE : PASS_TEMPLATE;
    }

    private static String extractChecklistVersion(String userPrompt) {
        if (userPrompt == null) return FALLBACK_CHECKLIST_VERSION;
        Matcher m = CHECKLIST_VERSION_MARKER.matcher(userPrompt);
        return m.find() ? m.group(1) : FALLBACK_CHECKLIST_VERSION;
    }
}
