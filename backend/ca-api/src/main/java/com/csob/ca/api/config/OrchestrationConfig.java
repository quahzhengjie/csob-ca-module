package com.csob.ca.api.config;

import com.csob.ca.ai.ModelClient;
import com.csob.ca.ai.prompt.ClasspathPromptLoader;
import com.csob.ca.ai.prompt.PromptAssembler;
import com.csob.ca.ai.prompt.PromptLoader;
import com.csob.ca.ai.prompt.TemplatePromptAssembler;
import com.csob.ca.ai.provider.HttpModelClient;
import com.csob.ca.ai.provider.StubModelClient;
import com.csob.ca.checklist.engine.ChecklistEngine;
import com.csob.ca.checklist.engine.ChecklistEngineImpl;
import com.csob.ca.checklist.version.ChecklistVersionResolver;
import com.csob.ca.checklist.version.DefaultChecklistVersionResolver;
import com.csob.ca.orchestration.PipelineCoordinator;
import com.csob.ca.orchestration.policy.RetryPolicy;
import com.csob.ca.orchestration.steps.EvaluateChecklistStep;
import com.csob.ca.orchestration.steps.InvokeAiStep;
import com.csob.ca.orchestration.steps.InvokeToolsStep;
import com.csob.ca.orchestration.steps.PersistStep;
import com.csob.ca.orchestration.steps.PipelineStep;
import com.csob.ca.orchestration.steps.ValidateAiStep;
import com.csob.ca.persistence.audit.AuditLogJpaRepository;
import com.csob.ca.persistence.audit.AuditReader;
import com.csob.ca.persistence.audit.AuditWriter;
import com.csob.ca.persistence.audit.DbAuditReader;
import com.csob.ca.persistence.audit.DbAuditWriter;
import com.csob.ca.persistence.repository.CaPackJpaRepository;
import com.csob.ca.persistence.repository.JpaPackRepository;
import com.csob.ca.persistence.repository.PackRepository;
import com.csob.ca.tools.adapter.DefaultToolInvoker;
import com.csob.ca.tools.adapter.DocumentMetadataSource;
import com.csob.ca.tools.adapter.DocumentMetadataTool;
import com.csob.ca.tools.adapter.DocumentMetadataToolAdapter;
import com.csob.ca.tools.adapter.FilesystemDocumentMetadataSource;
import com.csob.ca.tools.adapter.ToolInvoker;
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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * The ONE place that wires domain-module POJOs into Spring beans.
 * Domain modules must remain Spring-free; wiring happens here.
 *
 * TODO: split into sub-@Configuration classes (Ai, Validation, Checklist,
 * Tools, Persistence) before the module becomes large.
 */
@Configuration
public class OrchestrationConfig {

    // ----- AI layer -----
    @Bean
    public PromptLoader promptLoader() {
        return new ClasspathPromptLoader();
    }

    @Bean
    public PromptAssembler promptAssembler(PromptLoader loader, ObjectMapper objectMapper) {
        return new TemplatePromptAssembler(loader, objectMapper);
    }

    /**
     * ModelClient is property-driven:
     *   ca.ai.provider = STUB  (default) → canned responses for local dev and smoke tests
     *   ca.ai.provider = HTTP           → provider-agnostic HttpModelClient
     *
     * HTTP config keys (ignored when provider = STUB):
     *   ca.ai.http.endpoint          (required for HTTP)
     *   ca.ai.http.timeout-seconds   (default 30)
     *   ca.ai.http.max-attempts      (default 2; clamped to [1,3])
     *   ca.ai.http.auth-token        (optional; resolved from env var, NOT committed)
     */
    @Bean
    public ModelClient modelClient(
            @Value("${ca.ai.provider:STUB}") String provider,
            @Value("${ca.ai.http.endpoint:}") String httpEndpoint,
            @Value("${ca.ai.http.timeout-seconds:30}") int httpTimeoutSeconds,
            @Value("${ca.ai.http.max-attempts:2}") int httpMaxAttempts,
            @Value("${ca.ai.http.auth-token:}") String httpAuthToken,
            ObjectMapper objectMapper) {
        String normalised = (provider == null ? "STUB" : provider.trim().toUpperCase());
        switch (normalised) {
            case "STUB":
                return new StubModelClient();
            case "HTTP":
                if (httpEndpoint == null || httpEndpoint.isBlank()) {
                    throw new IllegalStateException(
                            "ca.ai.provider=HTTP requires ca.ai.http.endpoint to be set");
                }
                HttpClient transport = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                return new HttpModelClient(
                        transport,
                        URI.create(httpEndpoint),
                        Duration.ofSeconds(httpTimeoutSeconds),
                        httpMaxAttempts,
                        httpAuthToken,
                        objectMapper);
            default:
                throw new IllegalStateException(
                        "Unknown ca.ai.provider '" + provider + "' (expected STUB or HTTP)");
        }
    }

    // ----- Tool layer (source → adapter → invoker) -----
    /**
     * Document metadata source. Today: filesystem-backed. Tomorrow: swap
     * this single bean for a CsobDocumentMetadataSource implementing the
     * same {@link DocumentMetadataSource} interface — nothing else changes.
     */
    @Bean
    public DocumentMetadataSource documentMetadataSource(
            @Value("${ca.tools.documents.root:./sample-data/documents}") String rootPath,
            ObjectMapper objectMapper) {
        return new FilesystemDocumentMetadataSource(Paths.get(rootPath), objectMapper);
    }

    @Bean
    public DocumentMetadataTool documentMetadataTool(DocumentMetadataSource source) {
        return new DocumentMetadataToolAdapter(source);
    }

    /**
     * Generic dispatcher. Other tool types (PARTY / SCREENING / RELATED_PARTIES)
     * are not yet implemented and are passed as null — DefaultToolInvoker
     * throws UnsupportedOperationException when invoked for those ToolIds.
     */
    @Bean
    public ToolInvoker toolInvoker(DocumentMetadataTool documentMetadataTool) {
        return new DefaultToolInvoker(null, documentMetadataTool, null, null);
    }

    // ----- Checklist engine -----
    /**
     * Shared Clock bean — injected into the checklist engine and into every
     * rule that needs a notion of "today" (e.g. DocumentExpiryRule). A
     * single UTC system-clock bean by default; tests override at construction.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ChecklistVersionResolver checklistVersionResolver(Clock clock) {
        return new DefaultChecklistVersionResolver(clock);
    }

    @Bean
    public ChecklistEngine checklistEngine(ChecklistVersionResolver resolver, Clock clock) {
        return new ChecklistEngineImpl(resolver, clock);
    }

    // ----- Validation -----
    @Bean
    public Tokeniser tokeniser() {
        return new DefaultTokeniser();
    }

    @Bean
    public CitationResolver citationResolver() {
        return new DefaultCitationResolver();
    }

    @Bean
    public ValidationPipeline validationPipeline(ObjectMapper objectMapper,
                                                 CitationResolver resolver,
                                                 Tokeniser tokeniser) {
        // SchemaValidator is the ONLY check wired with a real implementation today.
        // ObjectMapper is Spring Boot's auto-configured bean (JSR-310 registered
        // via spring-boot-starter-json, pulled in by starter-web).
        List<ValidationCheck> ordered = List.of(
                new SchemaValidator(objectMapper),
                new PackBindingCheck(),
                new SectionWhitelistCheck(),
                new LengthGuardCheck(),
                new FormatGuardCheck(),
                new CitationPresenceCheck(),
                new CitationResolvabilityCheck(resolver),
                new FactGroundingCheck(resolver),
                new CoverageCheck(tokeniser),
                new BannedVocabularyCheck(BannedVocabularyCheck.loadDefaultTerms())
        );
        return new DefaultValidationPipeline(ordered);
    }

    // ----- Persistence (v1: H2-backed, minimal) -----
    @Bean
    public PackRepository packRepository(CaPackJpaRepository caPackJpaRepository,
                                         ObjectMapper objectMapper) {
        return new JpaPackRepository(caPackJpaRepository, objectMapper);
    }

    @Bean
    public AuditWriter auditWriter(AuditLogJpaRepository auditLogJpaRepository) {
        return new DbAuditWriter(auditLogJpaRepository);
    }

    @Bean
    public AuditReader auditReader(AuditLogJpaRepository auditLogJpaRepository) {
        return new DbAuditReader(auditLogJpaRepository);
    }

    // ----- Pipeline coordinator -----
    /**
     * Fixed, backend-controlled pipeline:
     *   INVOKE_TOOLS → EVALUATE_CHECKLIST → INVOKE_AI → VALIDATE_AI → PERSIST
     *
     * PersistStep writes the pack aggregate and emits the per-step audit
     * trail. Pipeline output (the CaPackDto returned by
     * PipelineCoordinator#run) is unchanged by the addition.
     */
    @Bean
    public PipelineCoordinator pipelineCoordinator(ToolInvoker toolInvoker,
                                                   ChecklistEngine checklistEngine,
                                                   PromptAssembler promptAssembler,
                                                   ModelClient modelClient,
                                                   ValidationPipeline validationPipeline,
                                                   PackRepository packRepository,
                                                   AuditWriter auditWriter,
                                                   ObjectMapper objectMapper,
                                                   Clock clock) {
        List<PipelineStep> steps = List.of(
                new InvokeToolsStep(toolInvoker),
                new EvaluateChecklistStep(checklistEngine),
                new InvokeAiStep(promptAssembler, modelClient, RetryPolicy.disabled(), objectMapper, clock),
                new ValidateAiStep(validationPipeline),
                new PersistStep(packRepository, auditWriter, clock)
        );
        return new PipelineCoordinator(steps, clock);
    }
}
