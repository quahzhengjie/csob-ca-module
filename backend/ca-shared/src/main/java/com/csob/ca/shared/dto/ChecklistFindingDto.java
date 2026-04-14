package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.RuleSeverity;
import com.csob.ca.shared.enums.RuleStatus;

import java.util.List;
import java.util.Objects;

public record ChecklistFindingDto(
        String ruleId,
        String description,
        String regulatoryReference,
        RuleSeverity severity,
        RuleStatus status,
        List<EvidenceDto> evidence
) {
    public ChecklistFindingDto {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(regulatoryReference, "regulatoryReference");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(status, "status");
        evidence = List.copyOf(evidence);
        if (status != RuleStatus.NOT_APPLICABLE && evidence.isEmpty()) {
            throw new IllegalArgumentException(
                    "evidence required for ruleId=" + ruleId + " with status=" + status);
        }
    }
}
