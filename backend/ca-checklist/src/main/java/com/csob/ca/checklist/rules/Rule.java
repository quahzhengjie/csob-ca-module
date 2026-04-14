package com.csob.ca.checklist.rules;

import com.csob.ca.shared.dto.ChecklistFindingDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.enums.RuleSeverity;

import java.util.List;

/**
 * A single deterministic rule. Stateless. Pure. Testable in isolation.
 * Implementations live under rules/v1, rules/v2, ... Never overwritten —
 * past packs replay against the version they were created with.
 */
public interface Rule {
    String ruleId();

    String description();

    String regulatoryReference();

    RuleSeverity severity();

    ChecklistFindingDto evaluate(List<ToolOutputDto> toolOutputs);
}
