package com.csob.ca.ai.prompt;

import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.PartyFactsDto;

public final class TemplatePromptAssembler implements PromptAssembler {

    private final PromptLoader promptLoader;

    public TemplatePromptAssembler(PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
    }

    @Override
    public AiRequest assemble(String packId,
                              String promptVersion,
                              PartyFactsDto partyFacts,
                              ChecklistResultDto checklistResult) {
        throw new UnsupportedOperationException(
                "Skeleton — load pinned templates via promptLoader and substitute " +
                "whitelisted fields only, for promptVersion=" + promptVersion);
    }
}
