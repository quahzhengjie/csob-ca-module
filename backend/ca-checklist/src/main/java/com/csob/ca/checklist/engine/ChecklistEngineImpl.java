package com.csob.ca.checklist.engine;

import com.csob.ca.checklist.version.ChecklistVersionResolver;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.ToolOutputDto;

import java.util.List;

public final class ChecklistEngineImpl implements ChecklistEngine {

    private final ChecklistVersionResolver versionResolver;

    public ChecklistEngineImpl(ChecklistVersionResolver versionResolver) {
        this.versionResolver = versionResolver;
    }

    @Override
    public ChecklistResultDto evaluate(String packId,
                                       String checklistVersion,
                                       List<ToolOutputDto> toolOutputs) {
        throw new UnsupportedOperationException(
                "Skeleton — implement deterministic rule evaluation for version=" + checklistVersion);
    }
}
