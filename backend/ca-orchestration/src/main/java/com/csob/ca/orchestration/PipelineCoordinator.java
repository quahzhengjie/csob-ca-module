package com.csob.ca.orchestration;

import com.csob.ca.orchestration.exception.PipelineException;
import com.csob.ca.orchestration.steps.PipelineStep;
import com.csob.ca.orchestration.steps.StepContext;
import com.csob.ca.shared.dto.CaPackDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.PartyFactsDto;
import com.csob.ca.shared.dto.RawAiOutputDto;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Executes the fixed, backend-controlled pipeline:
 *   INVOKE_TOOLS → EVALUATE_CHECKLIST → INVOKE_AI → VALIDATE_AI
 *
 * The coordinator is the ONLY class permitted to:
 *   - order pipeline steps
 *   - transition pack status (steps call StepContext#advanceStatus)
 *   - translate step failures into {@link PipelineException}
 *
 * It does NOT:
 *   - let the model choose a step
 *   - apply risk or decision semantics
 *   - persist anything (Phase 3)
 *   - run a reviewer flow (Phase 3+)
 *
 * Error semantics:
 *   - Any RuntimeException thrown by a step becomes a PipelineException
 *     carrying {@code packId} + step name for the audit trail.
 *   - InvokeAiStep deliberately catches its own exceptions (AI failures
 *     must not crash the JVM); it stores a synthetic MALFORMED RawAiOutput
 *     so ValidateAiStep still runs deterministically.
 *
 * Output:
 *   - A {@link CaPackDto} aggregate assembled from the final
 *     {@link StepContext}. Persistence is responsible for assigning a real
 *     {@code packVersion} and filling reviewer / audit fields; today those
 *     are placeholder values.
 */
public final class PipelineCoordinator {

    private static final String PLACEHOLDER_64_ZERO_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private final List<PipelineStep> orderedSteps;
    private final Clock clock;

    public PipelineCoordinator(List<PipelineStep> orderedSteps) {
        this(orderedSteps, Clock.systemUTC());
    }

    public PipelineCoordinator(List<PipelineStep> orderedSteps, Clock clock) {
        this.orderedSteps = List.copyOf(orderedSteps);
        this.clock        = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Run the pipeline for one pack. Returns a fully-populated
     * {@link CaPackDto}; callers (controllers, persistence layer) use this
     * as the single combined result of the pipeline execution.
     *
     * @param partyFacts whitelisted structured party snapshot. Today the
     *                   caller must provide this — a real PartyTool adapter
     *                   will supersede it when Phase 3 lands.
     * @param createdBy authenticated caller identity; stored immutably on
     *                  the pack record. May be {@code null}, in which case
     *                  "pipeline" is recorded.
     */
    public CaPackDto run(String packId,
                         String partyId,
                         String checklistVersion,
                         String promptVersion,
                         PartyFactsDto partyFacts,
                         String createdBy) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(checklistVersion, "checklistVersion");
        Objects.requireNonNull(promptVersion, "promptVersion");
        Objects.requireNonNull(partyFacts, "partyFacts");

        StepContext context = new StepContext(packId, partyId, checklistVersion, promptVersion);
        context.setPartyFacts(partyFacts);

        runSteps(context);

        return assemblePack(context, createdBy);
    }

    /** For tests: execute into a provided context; primarily to inspect state. */
    public StepContext runInto(StepContext context) {
        Objects.requireNonNull(context, "context");
        runSteps(context);
        return context;
    }

    // ---- internals ----

    private void runSteps(StepContext context) {
        for (PipelineStep step : orderedSteps) {
            try {
                step.execute(context);
            } catch (PipelineException alreadyWrapped) {
                throw alreadyWrapped;
            } catch (RuntimeException stepFailure) {
                throw new PipelineException(
                        context.packId(),
                        step.name(),
                        "Pipeline step '" + step.name() + "' failed: "
                                + (stepFailure.getMessage() == null
                                        ? stepFailure.getClass().getSimpleName()
                                        : stepFailure.getMessage()),
                        stepFailure);
            }
        }
    }

    private CaPackDto assemblePack(StepContext context, String createdBy) {
        RawAiOutputDto rawAi = context.rawAiOutput();
        ChecklistResultDto checklist = context.checklistResult();

        String modelId      = (rawAi != null) ? rawAi.modelId()      : "unavailable";
        String modelVersion = (rawAi != null) ? rawAi.modelVersion() : "unavailable";
        String hashRoot     = (checklist != null) ? checklist.toolOutputsHashRoot()
                                                  : PLACEHOLDER_64_ZERO_HASH;

        return new CaPackDto(
                context.packId(),
                1,                                      // packVersion — persistence assigns the real value
                context.partyId(),
                context.status(),
                (createdBy == null ? "pipeline" : createdBy),
                clock.instant(),
                context.checklistVersion(),
                context.promptVersion(),
                modelId,
                modelVersion,
                hashRoot,
                context.partyFacts(),
                checklist,
                rawAi,
                context.validationReport(),
                null,                                   // finalEditedSummary — analyst writes this at review time
                List.of(),                              // reviewerActions     — reviewer flow not wired yet
                List.of()                               // signOffChain        — reviewer flow not wired yet
        );
    }
}
