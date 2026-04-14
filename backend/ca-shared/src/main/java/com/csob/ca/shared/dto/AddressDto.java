package com.csob.ca.shared.dto;

import java.util.Objects;

public record AddressDto(
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String country
) {
    public AddressDto {
        Objects.requireNonNull(line1, "line1");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(country, "country");
    }
}
