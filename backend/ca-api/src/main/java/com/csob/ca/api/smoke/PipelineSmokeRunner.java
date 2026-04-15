package com.csob.ca.api.smoke;

import com.csob.ca.ai.ModelClient;
import com.csob.ca.ai.provider.StubModelClient;
import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.ai.request.AiResponse;
import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.ChecklistCompletionDto;
import com.csob.ca.shared.dto.ChecklistFindingDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.DocumentMetaDto;
import com.csob.ca.shared.dto.EvidenceDto;
import com.csob.ca.shared.dto.RawAiOutputDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.ValidationReportDto;
import com.csob.ca.shared.dto.tool.DocumentMetadataToolPayload;
import com.csob.ca.shared.enums.ParseStatus;
import com.csob.ca.shared.enums.RuleSeverity;
import com.csob.ca.shared.enums.RuleStatus;
import com.csob.ca.shared.enums.SourceType;
import com.csob.ca.shared.enums.ToolId;
import com.csob.ca.validation.DefaultValidationPipeline;
import com.csob.ca.validation.ValidationPipeline;
import com.csob.ca.validation.check.BannedVocabularyCheck;
import com.csob.ca.validation.check.CitationPresenceCheck;
import com.csob.ca.validation.check.CitationResolvabilityCheck;
import com.csob.ca.validation.check.CoverageCheck;
import com.csob.ca.validation.check.FactGroundingCheck;
import com.csob.ca.validation.check.FormatGuardCheck;
import com.csob.ca.validation.check.LengthGuardCheck;
import com.csob.ca.validation.check.PackBindingCheck;
import com.csob.ca.validation.check.SchemaValidator;
import com.csob.ca.validation.check.SectionWhitelistCheck;
import com.csob.ca.validation.check.ValidationCheck;
import com.csob.ca.validation.support.CitationResolver;
import com.csob.ca.validation.support.DefaultCitationResolver;
import com.csob.ca.validation.support.DefaultTokeniser;
import com.csob.ca.validation.support.Tokeniser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * End-to-end smoke runner. Wires a StubModelClient (bypassing PromptAssembler)
 * through the 10-check ValidationPipeline against mock fixtures. Prints two
 * reports: one PASS (default canned JSON) and one FAIL (date mismatch that
 * trips FACT_GROUNDING).
 *
 * Not a production code path. Not a JUnit test. Run with:
 *   ./mvnw -pl ca-api -am compile \
 *     org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
 *     -Dexec.mainClass=com.csob.ca.api.smoke.PipelineSmokeRunner
 */
public final class PipelineSmokeRunner {

    private PipelineSmokeRunner() { /* utility */ }

    // FAIL case: same structure as StubModelClient.DEFAULT_CANNED_JSON, but the
    // DATE factMention claims "2099-12-31" while the source is "2026-04-01".
    // Everything else still grounds and cites correctly, so the FAIL is isolated
    // to FACT_GROUNDING and provides a clean one-check failure demonstration.
    private static final String FAIL_CASE_JSON = """
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

    public static void main(String[] args) throws Exception {
        String packId = "pack-test-0001";
        String checklistVersion = "v1.0";

        ObjectMapper mapper = buildObjectMapper();
        List<ToolOutputDto> toolOutputs = mockToolOutputs(packId);
        ChecklistResultDto checklistResult = mockChecklistResult(packId, checklistVersion);
        ValidationPipeline pipeline = buildValidationPipeline(mapper);

        banner("SMOKE TEST 1 — PASS CASE (default canned JSON)");
        runOne("PASS-SCENARIO", StubModelClient.DEFAULT_CANNED_JSON,
                packId, checklistVersion, mapper, toolOutputs, checklistResult, pipeline);

        banner("SMOKE TEST 2 — FAIL CASE (DATE mismatch: 2099-12-31 vs source 2026-04-01)");
        runOne("FAIL-SCENARIO", FAIL_CASE_JSON,
                packId, checklistVersion, mapper, toolOutputs, checklistResult, pipeline);
    }

    // ---- one execution of the slice: Stub → RawAiOutput → Pipeline → Report ----

    private static void runOne(String label, String cannedJson,
                               String packId, String checklistVersion, ObjectMapper mapper,
                               List<ToolOutputDto> toolOutputs, ChecklistResultDto checklistResult,
                               ValidationPipeline pipeline) {
        ModelClient stub = new StubModelClient(cannedJson);
        AiRequest request = new AiRequest(
                packId,
                "v1",
                "(prompt-assembler bypassed in smoke test)",
                "(prompt-assembler bypassed in smoke test)",
                "(schema asset bypassed in smoke test)");

        AiResponse response = stub.invoke(request);
        RawAiOutputDto rawAiOutput = buildRawAiOutput(response, request.promptVersion(), mapper);

        ValidationReportDto report = pipeline.validate(
                packId, checklistVersion, toolOutputs, checklistResult, rawAiOutput);

        System.out.println("[" + label + "] raw JSON length       : " + response.rawJsonText().length());
        System.out.println("[" + label + "] parse status          : " + rawAiOutput.parseStatus());
        System.out.println("[" + label + "] report.status         : " + report.status());
        System.out.println("[" + label + "] report.validatedAt    : " + report.validatedAt());
        System.out.println("[" + label + "] checks run            : " + report.checks().size());
        System.out.println();
        System.out.println("  per-check results:");
        report.checks().forEach(c -> {
            String line = "    " + pad(c.checkId().name(), 26) + " " + c.status();
            if (!c.failures().isEmpty()) {
                line += "  (" + c.failures().size() + " failure(s))";
            }
            System.out.println(line);
            c.failures().forEach(f ->
                    System.out.println("        - " + f.code() + " @ " + f.locator()
                            + "  ::  " + f.detail()));
        });
    }

    // ---- fixtures ----

    private static List<ToolOutputDto> mockToolOutputs(String packId) {
        DocumentMetaDto doc = new DocumentMetaDto(
                "doc-001",
                "PASSPORT",
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2026, 4, 1),
                Instant.parse("2026-01-01T00:00:00Z"),
                10,
                "application/pdf",
                SIXTY_FOUR_ZEROS);

        ToolOutputDto docTool = new ToolOutputDto(
                "to-doc-0001",
                packId,
                ToolId.DOCUMENT_METADATA,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z"),
                "kyc-v1",
                new DocumentMetadataToolPayload(List.of(doc)),
                SIXTY_FOUR_ZEROS);
        return List.of(docTool);
    }

    private static ChecklistResultDto mockChecklistResult(String packId, String checklistVersion) {
        ChecklistFindingDto finding = new ChecklistFindingDto(
                "R-DOC-EXPIRED",
                "Document has expired",
                "MAS 626 §6.31",
                RuleSeverity.HIGH,
                RuleStatus.FAIL,
                List.of(new EvidenceDto(
                        SourceType.DOCUMENT_META,
                        "doc-001",
                        "expiresOn",
                        "2026-04-01")));
        return new ChecklistResultDto(
                packId,
                checklistVersion,
                Instant.parse("2026-04-15T00:00:00Z"),
                SIXTY_FOUR_ZEROS,
                List.of(finding),
                new ChecklistCompletionDto(1, 0, 1, 0, 0));
    }

    // ---- pipeline construction (mirrors OrchestrationConfig.validationPipeline) ----

    private static ValidationPipeline buildValidationPipeline(ObjectMapper mapper) {
        CitationResolver resolver = new DefaultCitationResolver();
        Tokeniser tokeniser = new DefaultTokeniser();
        List<ValidationCheck> ordered = List.of(
                new SchemaValidator(mapper),
                new PackBindingCheck(),
                new SectionWhitelistCheck(),
                new LengthGuardCheck(),
                new FormatGuardCheck(),
                new CitationPresenceCheck(),
                new CitationResolvabilityCheck(resolver),
                new FactGroundingCheck(resolver),
                new CoverageCheck(tokeniser),
                new BannedVocabularyCheck(BannedVocabularyCheck.loadDefaultTerms()));
        return new DefaultValidationPipeline(ordered);
    }

    // ---- helpers ----

    private static RawAiOutputDto buildRawAiOutput(AiResponse response,
                                                   String promptVersion,
                                                   ObjectMapper mapper) {
        AiSummaryPayloadDto payload = null;
        ParseStatus status;
        try {
            payload = mapper.readValue(response.rawJsonText(), AiSummaryPayloadDto.class);
            status = ParseStatus.PARSED;
        } catch (Exception e) {
            status = ParseStatus.MALFORMED;
        }
        return new RawAiOutputDto(
                response.packId(),
                response.modelId(),
                response.modelVersion(),
                promptVersion,
                response.generatedAt(),
                status,
                response.rawJsonText(),
                payload,
                SIXTY_FOUR_ZEROS);
    }

    private static ObjectMapper buildObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static void banner(String title) {
        String line = "=".repeat(72);
        System.out.println();
        System.out.println(line);
        System.out.println(title);
        System.out.println(line);
    }

    /** Placeholder 64-char string that satisfies hash-typed DTO fields (DTO only validates non-null). */
    private static final String SIXTY_FOUR_ZEROS =
            "0000000000000000000000000000000000000000000000000000000000000000";
}
