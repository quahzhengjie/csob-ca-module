package com.csob.ca.ai.prompt;

import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.PartyFactsDto;
import com.csob.ca.shared.dto.ToolOutputDto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic, model-agnostic prompt assembler.
 *
 * Behaviour:
 *  - Loads the pinned v{n} system prompt, user template, and output schema
 *    from {@link PromptLoader} (immutable strings, verbatim from classpath).
 *  - Serialises PartyFactsDto, ChecklistResultDto, and the ToolOutputDto
 *    list to pretty-printed JSON via the injected ObjectMapper.
 *  - Substitutes only the declared {{double-braced}} placeholders in the
 *    user template. No caller-supplied free text ever reaches the prompt.
 *  - Returns an AiRequest carrying BOTH the assembled user prompt (which
 *    embeds the schema inline) and the schema as a separate
 *    {@code outputSchemaJson} field so downstream providers can pass it
 *    via structured-output APIs (e.g. response_format) without re-reading.
 *
 * Not provider-specific: no OpenAI / Anthropic / Bedrock tokens or fields.
 */
public final class TemplatePromptAssembler implements PromptAssembler {

    private static final String PH_PACK_ID            = "{{packId}}";
    private static final String PH_CHECKLIST_VERSION  = "{{checklistVersion}}";
    private static final String PH_OUTPUT_SCHEMA      = "{{outputSchema}}";
    private static final String PH_PARTY_FACTS        = "{{partyFactsJson}}";
    private static final String PH_CHECKLIST_RESULT   = "{{checklistResultJson}}";
    private static final String PH_TOOL_OUTPUTS       = "{{toolOutputsJson}}";

    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    public TemplatePromptAssembler(PromptLoader promptLoader, ObjectMapper objectMapper) {
        this.promptLoader = Objects.requireNonNull(promptLoader, "promptLoader");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public AiRequest assemble(String packId,
                              String promptVersion,
                              PartyFactsDto partyFacts,
                              ChecklistResultDto checklistResult,
                              List<ToolOutputDto> toolOutputs) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(promptVersion, "promptVersion");
        Objects.requireNonNull(partyFacts, "partyFacts");
        Objects.requireNonNull(checklistResult, "checklistResult");
        List<ToolOutputDto> outs = (toolOutputs == null) ? List.of() : toolOutputs;

        String systemPrompt = promptLoader.loadSystemPrompt(promptVersion);
        String template     = promptLoader.loadUserPromptTemplate(promptVersion);
        String schemaJson   = promptLoader.loadOutputSchema(promptVersion);

        String partyJson     = toJson(partyFacts,      "partyFacts");
        String checklistJson = toJson(checklistResult, "checklistResult");
        String outputsJson   = toJson(outs,            "toolOutputs");

        String userPrompt = template
                .replace(PH_PACK_ID,           packId)
                .replace(PH_CHECKLIST_VERSION, checklistResult.checklistVersion())
                .replace(PH_OUTPUT_SCHEMA,     schemaJson)
                .replace(PH_PARTY_FACTS,       partyJson)
                .replace(PH_CHECKLIST_RESULT,  checklistJson)
                .replace(PH_TOOL_OUTPUTS,      outputsJson);

        return new AiRequest(packId, promptVersion, systemPrompt, userPrompt, schemaJson);
    }

    private String toJson(Object value, String what) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise " + what + " to JSON", e);
        }
    }
}
