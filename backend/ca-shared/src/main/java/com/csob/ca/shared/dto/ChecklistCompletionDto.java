package com.csob.ca.shared.dto;

public record ChecklistCompletionDto(
        int totalRules,
        int passed,
        int failed,
        int missing,
        int notApplicable
) {
    public ChecklistCompletionDto {
        if (totalRules < 0 || passed < 0 || failed < 0 || missing < 0 || notApplicable < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
        if (passed + failed + missing + notApplicable != totalRules) {
            throw new IllegalArgumentException("counts must sum to totalRules");
        }
    }
}
