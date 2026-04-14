package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.PartyType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The whitelisted, frozen snapshot of party information that may be passed
 * to the AI prompt. NO raw document text. NO OCR in v1.
 * Assembled by orchestration from PARTY + DOCUMENT_METADATA + SCREENING +
 * RELATED_PARTIES tool outputs.
 */
public record PartyFactsDto(
        String partyId,
        PartyType partyType,
        String legalName,
        List<String> otherNames,
        String primaryCountry,
        List<IdentifierDto> identifiers,
        AddressDto registeredAddress,
        List<AddressDto> contactAddresses,
        List<DocumentMetaDto> documentSummaries,
        List<ScreeningResultDto> screeningSummaries,
        List<RelatedPartyDto> relatedParties,
        IndividualDetailsDto individualDetails,
        OrganisationDetailsDto organisationDetails,
        Instant snapshotAt,
        String sourceVersion
) {
    public PartyFactsDto {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(partyType, "partyType");
        Objects.requireNonNull(legalName, "legalName");
        Objects.requireNonNull(primaryCountry, "primaryCountry");
        Objects.requireNonNull(registeredAddress, "registeredAddress");
        Objects.requireNonNull(snapshotAt, "snapshotAt");
        Objects.requireNonNull(sourceVersion, "sourceVersion");
        otherNames = List.copyOf(otherNames);
        identifiers = List.copyOf(identifiers);
        contactAddresses = List.copyOf(contactAddresses);
        documentSummaries = List.copyOf(documentSummaries);
        screeningSummaries = List.copyOf(screeningSummaries);
        relatedParties = List.copyOf(relatedParties);

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
