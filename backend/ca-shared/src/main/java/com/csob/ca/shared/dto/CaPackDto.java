package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.PackStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate read view of a pack. The CA module's API surface.
 * Fields marked advisory (rawAiOutput, finalEditedSummary) cannot affect
 * any system-of-record field — the database schema and mappers enforce this.
 */
public record CaPackDto(
        String packId,
        int packVersion,
        String partyId,
        PackStatus status,
        String createdBy,
        Instant createdAt,
        String checklistVersion,
        String promptVersion,
        String modelId,
        String modelVersion,
        String toolOutputsHashRoot,
        PartyFactsDto partyFacts,
        ChecklistResultDto checklistResult,
        RawAiOutputDto rawAiOutput,
        ValidationReportDto validationReport,
        FinalSummaryDto finalEditedSummary,
        List<ReviewerActionDto> reviewerActions,
        List<SignOffDto> signOffChain
) {
    public CaPackDto {
        Objects.requireNonNull(packId, "packId");
        if (packVersion < 1) {
            throw new IllegalArgumentException("packVersion must be >= 1");
        }
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(checklistVersion, "checklistVersion");
        Objects.requireNonNull(partyFacts, "partyFacts");
        Objects.requireNonNull(checklistResult, "checklistResult");
        reviewerActions = List.copyOf(reviewerActions);
        signOffChain = List.copyOf(signOffChain);
        if (signOffChain.size() > 2) {
            throw new IllegalArgumentException("signOffChain cannot exceed 2 entries (4-eyes)");
        }
    }
}
