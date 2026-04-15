package com.csob.ca.ai.prompt;

import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.PartyFactsDto;
import com.csob.ca.shared.dto.ToolOutputDto;

import java.util.List;

/**
 * Builds an AiRequest from pinned templates and structured inputs only.
 *
 * The assembler MUST NOT:
 *  - interpolate raw document text
 *  - accept analyst free-text into the prompt in v1
 *  - modify the loaded templates' instruction scaffolding
 *  - inject provider-specific tokens (keep output model-agnostic)
 *
 * It only substitutes whitelisted structured fields from the supplied DTOs
 * into declared slots in the user template.
 */
public interface PromptAssembler {

    /**
     * @param packId          the pipeline pack identifier
     * @param promptVersion   pinned prompt asset version (e.g. "v1")
     * @param partyFacts      whitelisted structured party snapshot
     * @param checklistResult authoritative deterministic findings
     * @param toolOutputs     frozen tool-output snapshots (may be empty; never null)
     * @return fully-assembled AiRequest ready to hand to any ModelClient
     */
    AiRequest assemble(String packId,
                       String promptVersion,
                       PartyFactsDto partyFacts,
                       ChecklistResultDto checklistResult,
                       List<ToolOutputDto> toolOutputs);
}
