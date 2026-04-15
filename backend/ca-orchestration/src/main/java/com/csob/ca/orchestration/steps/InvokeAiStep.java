package com.csob.ca.orchestration.steps;

import com.csob.ca.ai.ModelClient;
import com.csob.ca.ai.prompt.PromptAssembler;
import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.ai.request.AiResponse;
import com.csob.ca.orchestration.policy.RetryPolicy;
import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.RawAiOutputDto;
import com.csob.ca.shared.enums.PackStatus;
import com.csob.ca.shared.enums.ParseStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Objects;

/**
 * Third pipeline step. The ONLY step permitted to invoke the AI model.
 *
 * Success path:
 *   1. {@link PromptAssembler#assemble} builds an {@link AiRequest} from the
 *      checklist result, party facts, and tool outputs on the context.
 *   2. {@link ModelClient#invoke} returns an {@link AiResponse} carrying the
 *      raw JSON the model produced.
 *   3. Best-effort Jackson parse sets {@code parseStatus} truthfully on
 *      {@link RawAiOutputDto}; the validation layer re-parses via its
 *      own schema validator as the authoritative binding.
 *   4. Status advances to SUMMARISED.
 *
 * Failure path (any RuntimeException from PromptAssembler or ModelClient):
 *   - Caught HERE; a synthetic MALFORMED RawAiOutput is stored on the
 *     context carrying the error message in {@code rawJsonText}.
 *   - Status still advances to SUMMARISED so ValidateAiStep runs and the
 *     failure surfaces as a deterministic rejection in the report.
 *
 * This step NEVER throws out of {@link #execute(StepContext)} — crashing the
 * JVM on a model timeout would violate the pipeline's always-validate
 * invariant.
 */
public final class InvokeAiStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(InvokeAiStep.class);

    private static final String ZERO_64 =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private final PromptAssembler promptAssembler;
    private final ModelClient modelClient;
    private final RetryPolicy retryPolicy;    // reserved; HttpModelClient has its own transport retry today
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InvokeAiStep(PromptAssembler promptAssembler,
                        ModelClient modelClient,
                        RetryPolicy retryPolicy,
                        ObjectMapper objectMapper,
                        Clock clock) {
        this.promptAssembler = Objects.requireNonNull(promptAssembler, "promptAssembler");
        this.modelClient     = Objects.requireNonNull(modelClient, "modelClient");
        this.retryPolicy     = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.objectMapper    = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock           = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "INVOKE_AI";
    }

    @Override
    public void execute(StepContext context) {
        Objects.requireNonNull(context, "context");
        try {
            AiRequest request = promptAssembler.assemble(
                    context.packId(),
                    context.promptVersion(),
                    context.partyFacts(),
                    context.checklistResult(),
                    context.toolOutputs());

            AiResponse response = modelClient.invoke(request);
            context.setRawAiOutput(parseResponse(response, context.promptVersion()));
        } catch (RuntimeException failure) {
            log.error("AI call failed for packId={}; synthesising MALFORMED RawAiOutput so validation can still run",
                    context.packId(), failure);
            context.setRawAiOutput(synthesiseFailedOutput(context, failure));
        }
        context.advanceStatus(PackStatus.SUMMARISED);
    }

    // ---- helpers ----

    private RawAiOutputDto parseResponse(AiResponse response, String promptVersion) {
        AiSummaryPayloadDto payload = null;
        ParseStatus status;
        try {
            payload = objectMapper.readValue(response.rawJsonText(), AiSummaryPayloadDto.class);
            status = ParseStatus.PARSED;
        } catch (Exception parseFault) {
            status = ParseStatus.MALFORMED;
        }
        return new RawAiOutputDto(
                response.packId(),
                response.modelId(),
                response.modelVersion(),
                promptVersion,
                response.generatedAt(),
                status,
                response.rawJsonText(),
                payload,
                ZERO_64);
    }

    private RawAiOutputDto synthesiseFailedOutput(StepContext context, RuntimeException failure) {
        String message = "AI call failed: "
                + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        return new RawAiOutputDto(
                context.packId(),
                "unavailable",
                "unavailable",
                context.promptVersion(),
                clock.instant(),
                ParseStatus.MALFORMED,
                message,
                null,
                ZERO_64);
    }

    /** For introspection / tests only. */
    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }
}
