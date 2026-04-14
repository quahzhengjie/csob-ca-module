package com.csob.ca.shared.dto;

import java.time.LocalDate;
import java.util.Objects;

public record OrganisationDetailsDto(
        String registrationNumber,
        LocalDate dateOfIncorporation,
        String legalForm
) {
    public OrganisationDetailsDto {
        Objects.requireNonNull(registrationNumber, "registrationNumber");
        Objects.requireNonNull(dateOfIncorporation, "dateOfIncorporation");
        Objects.requireNonNull(legalForm, "legalForm");
    }
}
