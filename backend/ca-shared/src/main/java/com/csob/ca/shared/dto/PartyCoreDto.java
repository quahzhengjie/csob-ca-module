package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.PartyType;

import java.util.List;
import java.util.Objects;

/**
 * Lightweight identity-only view of a party. Returned by the PARTY tool and
 * merged into PartyFactsDto during orchestration. Carries NO documents,
 * screening results, or related-party graph — those are separate tool outputs.
 */
public record PartyCoreDto(
        String partyId,
        PartyType partyType,
        String legalName,
        List<String> otherNames,
        String primaryCountry,
        List<IdentifierDto> identifiers,
        AddressDto registeredAddress,
        List<AddressDto> contactAddresses,
        IndividualDetailsDto individualDetails,
        OrganisationDetailsDto organisationDetails
) {
    public PartyCoreDto {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(partyType, "partyType");
        Objects.requireNonNull(legalName, "legalName");
        Objects.requireNonNull(primaryCountry, "primaryCountry");
        Objects.requireNonNull(registeredAddress, "registeredAddress");
        otherNames = List.copyOf(otherNames);
        identifiers = List.copyOf(identifiers);
        contactAddresses = List.copyOf(contactAddresses);

        boolean isIndividual = partyType == PartyType.INDIVIDUAL;
        if (isIndividual && individualDetails == null) {
            throw new IllegalArgumentException("individualDetails required for INDIVIDUAL");
        }
        if (!isIndividual && organisationDetails == null) {
            throw new IllegalArgumentException("organisationDetails required for ORGANISATION");
        }
        if (isIndividual && organisationDetails != null) {
            throw new IllegalArgumentException("organisationDetails must be null for INDIVIDUAL");
        }
        if (!isIndividual && individualDetails != null) {
            throw new IllegalArgumentException("individualDetails must be null for ORGANISATION");
        }
    }
}
