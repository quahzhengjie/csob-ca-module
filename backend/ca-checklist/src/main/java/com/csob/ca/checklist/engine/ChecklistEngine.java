package com.csob.ca.checklist.engine;

import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.ToolOutputDto;

import java.util.List;

/**
 * Deterministic evaluator. Pure function over a frozen ToolOutput snapshot.
 *
 * Contract:
 *  - Same inputs + same checklistVersion MUST produce the same output.
 *  - No I/O. No clock reads beyond evaluatedAt capture. No randomness.
 *  - Must NOT depend on ca-ai-client, ca-validation, ca-orchestration.
 */
public interface ChecklistEngine {
    ChecklistResultDto evaluate(String packId,
                                String checklistVersion,
                                List<ToolOutputDto> toolOutputs);
}
