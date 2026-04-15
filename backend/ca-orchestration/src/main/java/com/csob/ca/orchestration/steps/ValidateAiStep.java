package com.csob.ca.orchestration.steps;

import com.csob.ca.shared.dto.RawAiOutputDto;
import com.csob.ca.shared.dto.ValidationReportDto;
import com.csob.ca.shared.enums.PackStatus;
import com.csob.ca.validation.ValidationPipeline;

import java.util.Objects;

/**
 * Fourth pipeline step. Runs the deterministic {@link ValidationPipeline}
 * over the {@link RawAiOutputDto} produced by {@link InvokeAiStep} and
 * stores the resulting {@link ValidationReportDto} on the context.
 *
 * Always-runs invariant: if {@code InvokeAiStep} caught a RuntimeException
 * it will have left a synthetic MALFORMED RawAiOutput on the context,
 * which is still a valid input for validation — the schema check will
 * reject it and downstream checks will record SCHEMA_NOT_PASSED. Human
 * review proceeds using the authoritative ChecklistResult alone.
 *
 * Only advances status to VALIDATED. Does NOT decide REVIEWED /
 * APPROVED_FOR_FILE — those are reviewer actions (not yet wired).
 */
public final class ValidateAiStep implements PipelineStep {

    private final ValidationPipeline validationPipeline;

    public ValidateAiStep(ValidationPipeline validationPipeline) {
        this.validationPipeline = Objects.requireNonNull(validationPipeline, "validationPipeline");
    }

    @Override
    public String name() {
        return "VALIDATE_AI";
    }

    @Override
    public void execute(StepContext context) {
        Objects.requireNonNull(context, "context");

        RawAiOutputDto rawAi = context.rawAiOutput();
        if (rawAi == null) {
            // Defensive: InvokeAiStep is supposed to always populate this.
            // If we get here, validation has nothing to check — just advance.
            context.advanceStatus(PackStatus.VALIDATED);
            return;
        }

        ValidationReportDto report = validationPipeline.validate(
                context.packId(),
                context.checklistVersion(),
                context.toolOutputs(),
                context.checklistResult(),
                rawAi);
        context.setValidationReport(report);
        context.advanceStatus(PackStatus.VALIDATED);
    }
}
