package com.csob.ca.orchestration.steps;

import com.csob.ca.tools.adapter.ToolInvoker;

/**
 * Invokes tools in a FIXED order (PARTY, DOCUMENT_METADATA, SCREENING,
 * RELATED_PARTIES). Freezes each result, hashes the payload, and appends
 * to the context's toolOutputs. Finally assembles PartyFactsDto from the
 * four frozen outputs.
 */
public final class GatherDataStep implements PipelineStep {

    private final ToolInvoker toolInvoker;

    public GatherDataStep(ToolInvoker toolInvoker) {
        this.toolInvoker = toolInvoker;
    }

    @Override
    public String name() {
        return "GATHER_DATA";
    }

    @Override
    public void execute(StepContext context) {
        throw new UnsupportedOperationException(
                "Skeleton — invoke tools in fixed order, append ToolOutputs, " +
                "assemble PartyFactsDto, advance status to DATA_GATHERED.");
    }
}
