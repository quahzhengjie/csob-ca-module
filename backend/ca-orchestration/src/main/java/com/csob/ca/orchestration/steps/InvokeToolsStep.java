package com.csob.ca.orchestration.steps;

import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.enums.PackStatus;
import com.csob.ca.shared.enums.ToolId;
import com.csob.ca.tools.adapter.ToolInvoker;

import java.util.List;
import java.util.Objects;

/**
 * First pipeline step. Calls the backend-controlled list of {@link ToolId}s
 * in fixed order, appends each frozen {@link ToolOutputDto} onto the
 * {@link StepContext}, and advances the pack status to DATA_GATHERED.
 *
 * Today the step invokes only {@link ToolId#DOCUMENT_METADATA} — the single
 * tool for which a real adapter exists. More ToolIds are added here as
 * their adapters are implemented; the order is stable per commit (so
 * replay against a historical pack yields identical ToolOutput ordering).
 *
 * Failures from the {@link ToolInvoker} (filesystem unreachable, upstream
 * source down, etc.) propagate as runtime exceptions — the coordinator
 * wraps them in {@link com.csob.ca.orchestration.exception.PipelineException}
 * so the step name appears in the audit trail.
 */
public final class InvokeToolsStep implements PipelineStep {

    /**
     * Stable, backend-controlled ordering of tools to invoke for every pack.
     * Adding a ToolId is a deliberate contract change and must be paired
     * with a checklist version bump so replay stays stable.
     */
    private static final List<ToolId> ORDERED_TOOL_IDS = List.of(
            ToolId.DOCUMENT_METADATA
            // ToolId.PARTY, ToolId.SCREENING, ToolId.RELATED_PARTIES — add when their adapters land.
    );

    private final ToolInvoker toolInvoker;

    public InvokeToolsStep(ToolInvoker toolInvoker) {
        this.toolInvoker = Objects.requireNonNull(toolInvoker, "toolInvoker");
    }

    @Override
    public String name() {
        return "INVOKE_TOOLS";
    }

    @Override
    public void execute(StepContext context) {
        Objects.requireNonNull(context, "context");
        for (ToolId toolId : ORDERED_TOOL_IDS) {
            ToolOutputDto output = toolInvoker.invoke(context.packId(), toolId, context.partyId());
            context.addToolOutput(output);
        }
        context.advanceStatus(PackStatus.DATA_GATHERED);
    }
}
