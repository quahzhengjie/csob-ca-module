package com.csob.ca.shared.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Authoritative findings. System of record.
 * Produced deterministically by ca-checklist from a frozen ToolOutput snapshot.
 * Nothing downstream (including AI output and analyst edits) may overwrite any
 * field in this DTO.
 */
public record ChecklistResultDto(
        String packId,
        String checklistVersion,
        Instant evaluatedAt,
        String toolOutputsHashRoot,
        List<ChecklistFindingDto> findings,
        ChecklistCompletionDto completion
) {
    public ChecklistResultDto {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(checklistVersion, "checklistVersion");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(toolOutputsHashRoot, "toolOutputsHashRoot");
        Objects.requireNonNull(completion, "completion");
        findings = List.copyOf(findings);
        if (findings.isEmpty()) {
            throw new IllegalArgumentException("findings must be non-empty");
        }
    }
}
