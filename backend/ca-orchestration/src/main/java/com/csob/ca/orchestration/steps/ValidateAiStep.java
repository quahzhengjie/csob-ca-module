package com.csob.ca.orchestration.steps;

import com.csob.ca.validation.ValidationPipeline;

/**
 * Runs the deterministic ValidationPipeline over the RawAiOutput.
 * On REJECTED the pack still advances — the summary surface is simply
 * suppressed in the UI. Human review proceeds using the authoritative
 * ChecklistResult alone.
 */
public final class ValidateAiStep implements PipelineStep {

    private final ValidationPipeline validationPipeline;

    public ValidateAiStep(ValidationPipeline validationPipeline) {
        this.validationPipeline = validationPipeline;
    }

    @Override
    public String name() {
        return "VALIDATE_AI";
    }

    @Override
    public void execute(StepContext context) {
        throw new UnsupportedOperationException(
                "Skeleton — invoke validationPipeline.validate, store ValidationReport, " +
                "advance status to VALIDATED.");
    }
}
