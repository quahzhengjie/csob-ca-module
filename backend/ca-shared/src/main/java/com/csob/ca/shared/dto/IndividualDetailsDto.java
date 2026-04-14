package com.csob.ca.shared.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record IndividualDetailsDto(
        LocalDate dateOfBirth,
        List<String> nationalities
) {
    public IndividualDetailsDto {
        Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        nationalities = List.copyOf(nationalities);
    }
}
