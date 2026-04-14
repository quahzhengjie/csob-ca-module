package com.csob.ca.orchestration.steps;

import com.csob.ca.ai.ModelClient;
import com.csob.ca.ai.prompt.PromptAssembler;
import com.csob.ca.orchestration.policy.RetryPolicy;

/**
 * The ONLY step permitted to invoke the AI model. Exactly one call per pack
 * by default (RetryPolicy.disabled()). If policy permits a bounded retry,
 * inputs are identical and every attempt is logged.
 */
public final class InvokeAiStep implements PipelineStep {

    private final PromptAssembler promptAssembler;
    private final ModelClient modelClient;
    private final RetryPolicy retryPolicy;

    public InvokeAiStep(PromptAssembler promptAssembler,
                        ModelClient modelClient,
                        RetryPolicy retryPolicy) {
        this.promptAssembler = promptAssembler;
        this.modelClient = modelClient;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public String name() {
        return "INVOKE_AI";
    }

    @Override
    public void execute(StepContext context) {
        throw new UnsupportedOperationException(
                "Skeleton — assemble AiRequest from partyFacts + checklistResult, " +
                "invoke modelClient once (honouring retryPolicy), store RawAiOutput, " +
                "advance status to SUMMARISED.");
    }
}
