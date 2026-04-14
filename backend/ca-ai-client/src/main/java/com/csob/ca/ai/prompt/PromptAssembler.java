package com.csob.ca.ai.prompt;

import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.PartyFactsDto;

/**
 * Builds an AiRequest from pinned templates and structured inputs only.
 *
 * The assembler MUST NOT:
 *  - interpolate raw document text
 *  - accept analyst free-text into the prompt in v1
 *  - modify the loaded templates' instruction scaffolding
 *
 * It only substitutes whitelisted structured fields from PartyFactsDto and
 * ChecklistResultDto into declared slots in the user template.
 */
public interface PromptAssembler {
    AiRequest assemble(String packId,
                       String promptVersion,
                       PartyFactsDto partyFacts,
                       ChecklistResultDto checklistResult);
}
