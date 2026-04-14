package com.csob.ca.orchestration;

import com.csob.ca.orchestration.steps.PipelineStep;
import com.csob.ca.orchestration.steps.StepContext;
import com.csob.ca.shared.dto.CaPackDto;

import java.util.List;

/**
 * Executes the fixed, backend-controlled pipeline:
 *   GATHER_DATA → RUN_CHECKLIST → INVOKE_AI → VALIDATE_AI → PERSIST
 *
 * The coordinator is the ONLY class permitted to:
 *   - order pipeline steps
 *   - transition pack status
 *   - decide when the pipeline has completed
 *
 * It does NOT:
 *   - let the model choose a step
 *   - skip the validation gate
 *   - apply risk or decision semantics
 */
public final class PipelineCoordinator {

    private final List<PipelineStep> orderedSteps;

    /**
     * @param orderedSteps ordered exactly as listed in the class Javadoc.
     */
    public PipelineCoordinator(List<PipelineStep> orderedSteps) {
        this.orderedSteps = List.copyOf(orderedSteps);
    }

    public CaPackDto run(String packId,
                         String partyId,
                         String checklistVersion,
                         String promptVersion) {
        throw new UnsupportedOperationException(
                "Skeleton — construct StepContext, execute each step in order, " +
                "catch PipelineException to persist a failure event, and return " +
                "the assembled CaPackDto.");
    }

    /** For tests: execute into a provided context (primarily to inspect state). */
    public StepContext runInto(StepContext context) {
        throw new UnsupportedOperationException(
                "Skeleton — iterate orderedSteps and invoke execute(context).");
    }
}
