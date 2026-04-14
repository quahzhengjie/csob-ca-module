package com.csob.ca.shared.dto;

import java.math.BigDecimal;
import java.util.Objects;

public record RelatedPartyDto(
        String relatedPartyId,
        String relationship,
        BigDecimal ownershipPct,
        boolean isController
) {
    public RelatedPartyDto {
        Objects.requireNonNull(relatedPartyId, "relatedPartyId");
        Objects.requireNonNull(relationship, "relationship");
    }
}
