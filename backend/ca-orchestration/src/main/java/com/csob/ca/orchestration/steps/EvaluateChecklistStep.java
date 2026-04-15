package com.csob.ca.orchestration.steps;

import com.csob.ca.checklist.engine.ChecklistEngine;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.enums.PackStatus;

import java.util.Objects;

/**
 * Second pipeline step. Runs the deterministic {@link ChecklistEngine} over
 * the frozen ToolOutput snapshot captured by InvokeToolsStep and stores the
 * authoritative {@link ChecklistResultDto} on the context.
 *
 * This is the step whose output is the system of record. Nothing downstream
 * — including AI summarisation and analyst edits — may modify it.
 *
 * Advances pack status to CHECKED.
 */
public final class EvaluateChecklistStep implements PipelineStep {

    private final ChecklistEngine checklistEngine;

    public EvaluateChecklistStep(ChecklistEngine checklistEngine) {
        this.checklistEngine = Objects.requireNonNull(checklistEngine, "checklistEngine");
    }

    @Override
    public String name() {
        return "EVALUATE_CHECKLIST";
    }

    @Override
    public void execute(StepContext context) {
        Objects.requireNonNull(context, "context");
        ChecklistResultDto result = checklistEngine.evaluate(
                context.packId(),
                context.checklistVersion(),
                context.toolOutputs());
        context.setChecklistResult(result);
        context.advanceStatus(PackStatus.CHECKED);
    }
}
