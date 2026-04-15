package com.csob.ca.api.smoke;

import com.csob.ca.ai.ModelClient;
import com.csob.ca.ai.prompt.ClasspathPromptLoader;
import com.csob.ca.ai.prompt.PromptAssembler;
import com.csob.ca.ai.prompt.PromptLoader;
import com.csob.ca.ai.prompt.TemplatePromptAssembler;
import com.csob.ca.ai.provider.StubModelClient;
import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.ai.request.AiResponse;
import com.csob.ca.checklist.engine.ChecklistEngine;
import com.csob.ca.checklist.engine.ChecklistEngineImpl;
import com.csob.ca.checklist.version.ChecklistVersionResolver;
import com.csob.ca.checklist.version.DefaultChecklistVersionResolver;
import com.csob.ca.shared.dto.AddressDto;
import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.ChecklistFindingDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.IndividualDetailsDto;
import com.csob.ca.shared.dto.PartyFactsDto;
import com.csob.ca.shared.dto.RawAiOutputDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.ValidationReportDto;
import com.csob.ca.tools.adapter.DefaultToolInvoker;
import com.csob.ca.tools.adapter.DocumentMetadataSource;
import com.csob.ca.tools.adapter.DocumentMetadataTool;
import com.csob.ca.tools.adapter.DocumentMetadataToolAdapter;
import com.csob.ca.tools.adapter.FilesystemDocumentMetadataSource;
import com.csob.ca.tools.adapter.ToolInvoker;
import com.csob.ca.shared.enums.ParseStatus;
import com.csob.ca.shared.enums.PartyType;
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * End-to-end smoke runner. Drives the PromptAssembler + StubModelClient +
 * 10-check ValidationPipeline against mock fixtures, printing:
 *   - the full assembled prompt (system + user)
 *   - the stub's canned AI response
 *   - the ValidationReportDto for two scenarios (PASS and FAIL)
 *
 * Not a production code path. Not a JUnit test. Run with:
 *   ./mvnw -q install -DskipTests
 *   ./mvnw -pl ca-api -q \
 *     org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
 *     -Dexec.mainClass=com.csob.ca.api.smoke.PipelineSmokeRunner
 */
public final class PipelineSmokeRunner {

    private PipelineSmokeRunner() { /* utility */ }

    private static final String PROMPT_VERSION = "v1";

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

        // Fixed clock so the smoke test stays deterministic indefinitely,
        // regardless of when it's run. Chosen deliberately after
        // party-0001/doc-001's expiresOn (2026-04-01) so DocumentExpiryRule
        // fires FAIL against the filesystem fixture.
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-15T00:00:00Z"), ZoneOffset.UTC);

        // fixtures
        PartyFactsDto partyFacts = mockPartyFacts(packId);
        List<ToolOutputDto> toolOutputs = loadToolOutputs(packId, mapper);
        ChecklistResultDto checklistResult = evaluateChecklist(
                packId, checklistVersion, toolOutputs, fixedClock);

        // assemble the prompt ONCE and print it - both scenarios reuse it
        // (the stub ignores prompt content, but downstream ModelClient
        // implementations will not).
        PromptLoader promptLoader = new ClasspathPromptLoader();
        PromptAssembler assembler = new TemplatePromptAssembler(promptLoader, mapper);
        AiRequest assembledRequest = assembler.assemble(
                packId, PROMPT_VERSION, partyFacts, checklistResult, toolOutputs);

        banner("ASSEMBLED PROMPT (v1, produced by TemplatePromptAssembler)");
        System.out.println("---- system ----");
        System.out.println(assembledRequest.systemPrompt());
        System.out.println("---- user ----");
        System.out.println(assembledRequest.userPrompt());
        System.out.println("---- meta ----");
        System.out.println("packId         : " + assembledRequest.packId());
        System.out.println("promptVersion  : " + assembledRequest.promptVersion());
        System.out.println("user length    : " + assembledRequest.userPrompt().length() + " chars");
        System.out.println("schema length  : " + assembledRequest.outputSchemaJson().length() + " chars");

        ValidationPipeline pipeline = buildValidationPipeline(mapper);

        banner("SMOKE TEST 1 - PASS CASE (default canned JSON)");
        runOne("PASS-SCENARIO", StubModelClient.DEFAULT_CANNED_JSON,
                packId, checklistVersion, mapper, toolOutputs, checklistResult,
                assembledRequest, pipeline);

        banner("SMOKE TEST 2 - FAIL CASE (DATE mismatch: 2099-12-31 vs source 2026-04-01)");
        runOne("FAIL-SCENARIO", FAIL_CASE_JSON,
                packId, checklistVersion, mapper, toolOutputs, checklistResult,
                assembledRequest, pipeline);
    }

    // ---- one execution: Stub(AiRequest) -> RawAiOutput -> Pipeline -> Report ----

    private static void runOne(String label,
                               String cannedJson,
                               String packId,
                               String checklistVersion,
                               ObjectMapper mapper,
                               List<ToolOutputDto> toolOutputs,
                               ChecklistResultDto checklistResult,
                               AiRequest assembledRequest,
                               ValidationPipeline pipeline) {
        ModelClient stub = new StubModelClient(cannedJson);

        AiResponse response = stub.invoke(assembledRequest);
        RawAiOutputDto rawAiOutput = buildRawAiOutput(response, assembledRequest.promptVersion(), mapper);

        ValidationReportDto report = pipeline.validate(
                packId, checklistVersion, toolOutputs, checklistResult, rawAiOutput);

        System.out.println("[" + label + "] stub raw JSON length  : " + response.rawJsonText().length());
        System.out.println("[" + label + "] parse status          : " + rawAiOutput.parseStatus());
        System.out.println("[" + label + "] report.status         : " + report.status());
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

    private static PartyFactsDto mockPartyFacts(String packId) {
        return new PartyFactsDto(
                "party-0001",
                PartyType.INDIVIDUAL,
                "Alex Tan",
                List.of(),
                "SG",
                List.of(),
                new AddressDto("1 Raffles Place", null, "Singapore", null, "048616", "SG"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new IndividualDetailsDto(LocalDate.of(1985, 3, 22), List.of("SG")),
                null,
                Instant.parse("2026-04-15T00:00:00Z"),
                "kyc-v1");
    }

    /**
     * Load tool outputs via the real filesystem-backed source and adapter,
     * dispatched through DefaultToolInvoker — exactly what the pipeline
     * will do in production (minus a CSOB-backed source instead of a
     * filesystem one). No hardcoded fixtures in the smoke runner anymore.
     *
     * The sample-data directory location defaults to "./sample-data/documents"
     * relative to user.dir. Maven `exec:java` preserves user.dir from the
     * shell, so running `./mvnw -pl ca-api exec:java` from `backend/`
     * resolves to backend/sample-data/documents. Override with
     * -Dca.smoke.sampleDataDir=/abs/path if launching from elsewhere.
     */
    private static List<ToolOutputDto> loadToolOutputs(String packId, ObjectMapper mapper) {
        Path sampleDir = Paths.get(System.getProperty(
                "ca.smoke.sampleDataDir",
                "./sample-data/documents")).toAbsolutePath().normalize();

        DocumentMetadataSource source = new FilesystemDocumentMetadataSource(sampleDir, mapper);
        DocumentMetadataTool   adapter = new DocumentMetadataToolAdapter(source);
        ToolInvoker invoker = new DefaultToolInvoker(null, adapter, null, null);

        ToolOutputDto docTool = invoker.invoke(packId, ToolId.DOCUMENT_METADATA, "party-0001");
        System.out.println("[fixtures] sample-data dir: " + sampleDir);
        System.out.println("[fixtures] loaded docs for party-0001: "
                + docTool.toolOutputId() + " (payload="
                + docTool.payload().getClass().getSimpleName() + ")");
        return List.of(docTool);
    }

    /**
     * Run the real deterministic ChecklistEngine against the pipeline's
     * frozen ToolOutputs. No hardcoded findings here — the result is fully
     * derived from (a) the rule set pinned to {@code checklistVersion} and
     * (b) the filesystem-backed document fixtures loaded upstream.
     *
     * The clock is pinned to 2026-04-15 UTC so PASS/FAIL semantics hold
     * regardless of wall-clock drift.
     */
    private static ChecklistResultDto evaluateChecklist(String packId,
                                                        String checklistVersion,
                                                        List<ToolOutputDto> toolOutputs,
                                                        Clock clock) {
        ChecklistVersionResolver resolver = new DefaultChecklistVersionResolver(clock);
        ChecklistEngine engine = new ChecklistEngineImpl(resolver, clock);
        ChecklistResultDto result = engine.evaluate(packId, checklistVersion, toolOutputs);

        System.out.println("[checklist] version=" + result.checklistVersion()
                + " evaluatedAt=" + result.evaluatedAt()
                + " totalRules=" + result.completion().totalRules()
                + " pass=" + result.completion().passed()
                + " fail=" + result.completion().failed()
                + " missing=" + result.completion().missing()
                + " n/a=" + result.completion().notApplicable());
        for (ChecklistFindingDto f : result.findings()) {
            System.out.println("[checklist]   " + f.ruleId() + "  " + f.status()
                    + " (" + f.evidence().size() + " evidence)");
            f.evidence().forEach(e -> System.out.println(
                    "[checklist]     - " + e.sourceType() + "/" + e.sourceId()
                            + "." + e.fieldPath() + " = " + e.observedValue()));
        }
        return result;
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

    private static final String SIXTY_FOUR_ZEROS =
            "0000000000000000000000000000000000000000000000000000000000000000";
}
