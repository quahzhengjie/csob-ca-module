package com.csob.ca.shared.dto;

import java.util.Objects;

public record ValidationFailureDto(
        String code,
        String locator,
        String detail
) {
    public ValidationFailureDto {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(detail, "detail");
    }
}
