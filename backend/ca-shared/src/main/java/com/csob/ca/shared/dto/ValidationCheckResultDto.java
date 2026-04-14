package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.CheckStatus;
import com.csob.ca.shared.enums.ValidationCheckId;

import java.util.List;
import java.util.Objects;

public record ValidationCheckResultDto(
        ValidationCheckId checkId,
        CheckStatus status,
        List<ValidationFailureDto> failures
) {
    public ValidationCheckResultDto {
        Objects.requireNonNull(checkId, "checkId");
        Objects.requireNonNull(status, "status");
        failures = List.copyOf(failures);
        if (status == CheckStatus.PASS && !failures.isEmpty()) {
            throw new IllegalArgumentException("failures must be empty when status = PASS");
        }
    }
}
