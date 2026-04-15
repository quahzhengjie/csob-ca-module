package com.csob.ca.api.config;

import com.csob.ca.ai.ModelClient;
import com.csob.ca.ai.prompt.ClasspathPromptLoader;
import com.csob.ca.ai.prompt.PromptAssembler;
import com.csob.ca.ai.prompt.PromptLoader;
import com.csob.ca.ai.prompt.TemplatePromptAssembler;
import com.csob.ca.ai.provider.StubModelClient;
import com.csob.ca.checklist.engine.ChecklistEngine;
import com.csob.ca.checklist.engine.ChecklistEngineImpl;
import com.csob.ca.checklist.version.ChecklistVersionResolver;
import com.csob.ca.orchestration.PipelineCoordinator;
import com.csob.ca.orchestration.policy.RetryPolicy;
import com.csob.ca.orchestration.steps.GatherDataStep;
import com.csob.ca.orchestration.steps.InvokeAiStep;
import com.csob.ca.orchestration.steps.PersistStep;
import com.csob.ca.orchestration.steps.PipelineStep;
import com.csob.ca.orchestration.steps.RunChecklistStep;
import com.csob.ca.orchestration.steps.ValidateAiStep;
import com.csob.ca.persistence.audit.AuditWriter;
import com.csob.ca.persistence.repository.PackRepository;
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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    public ModelClient modelClient() {
        // TODO: profile-switch between StubModelClient and the real provider.
        return new StubModelClient();
    }

    // ----- Checklist engine -----
    @Bean
    public ChecklistEngine checklistEngine(ChecklistVersionResolver resolver) {
        return new ChecklistEngineImpl(resolver);
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

    // ----- Pipeline coordinator -----
    @Bean
    public PipelineCoordinator pipelineCoordinator(ToolInvoker toolInvoker,
                                                   ChecklistEngine checklistEngine,
                                                   PromptAssembler promptAssembler,
                                                   ModelClient modelClient,
                                                   ValidationPipeline validationPipeline,
                                                   PackRepository packRepository,
                                                   AuditWriter auditWriter) {
        List<PipelineStep> steps = List.of(
                new GatherDataStep(toolInvoker),
                new RunChecklistStep(checklistEngine),
                new InvokeAiStep(promptAssembler, modelClient, RetryPolicy.disabled()),
                new ValidateAiStep(validationPipeline),
                new PersistStep(packRepository, auditWriter)
        );
        return new PipelineCoordinator(steps);
    }
}
