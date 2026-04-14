package com.csob.ca.ai.request;

import java.util.Objects;

/**
 * Fully-assembled request to the model. Constructed by PromptAssembler.
 * No caller-supplied free text leaks into systemPrompt or userPrompt —
 * both come from pinned templates with structured variable substitution only.
 */
public record AiRequest(
        String packId,
        String promptVersion,
        String systemPrompt,
        String userPrompt,
        String outputSchemaJson
) {
    public AiRequest {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(promptVersion, "promptVersion");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");
        Objects.requireNonNull(outputSchemaJson, "outputSchemaJson");
    }
}
