package com.csob.ca.orchestration.steps;

import com.csob.ca.checklist.engine.ChecklistEngine;

public final class RunChecklistStep implements PipelineStep {

    private final ChecklistEngine checklistEngine;

    public RunChecklistStep(ChecklistEngine checklistEngine) {
        this.checklistEngine = checklistEngine;
    }

    @Override
    public String name() {
        return "RUN_CHECKLIST";
    }

    @Override
    public void execute(StepContext context) {
        throw new UnsupportedOperationException(
                "Skeleton — invoke checklistEngine.evaluate, store authoritative " +
                "ChecklistResult, advance status to CHECKED.");
    }
}
