package com.csob.ca.shared.dto;

import java.time.LocalDate;
import java.util.Objects;

public record IdentifierDto(
        String type,
        String value,
        String issuingCountry,
        LocalDate expiresOn
) {
    public IdentifierDto {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(issuingCountry, "issuingCountry");
    }
}
