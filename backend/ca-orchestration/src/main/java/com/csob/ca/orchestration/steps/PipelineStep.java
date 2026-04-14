package com.csob.ca.orchestration.steps;

/**
 * One step of the linear, backend-controlled pipeline.
 *
 * Constraints:
 *  - Steps are executed in a fixed order by PipelineCoordinator.
 *  - A step MUST NOT choose the next step; the coordinator owns ordering.
 *  - A step MUST NOT call the AI model except where explicitly declared
 *    (only InvokeAiStep is permitted to).
 *  - A step MUST NOT perform risk rating or onboarding decision logic.
 */
public interface PipelineStep {

    String name();

    void execute(StepContext context);
}
