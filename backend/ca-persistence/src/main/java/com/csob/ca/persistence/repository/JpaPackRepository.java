package com.csob.ca.persistence.repository;

import com.csob.ca.persistence.entity.CaPackEntity;
import com.csob.ca.shared.dto.CaPackDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.FinalSummaryDto;
import com.csob.ca.shared.dto.PartyFactsDto;
import com.csob.ca.shared.dto.RawAiOutputDto;
import com.csob.ca.shared.dto.ReviewerActionDto;
import com.csob.ca.shared.dto.SignOffDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.ValidationReportDto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link PackRepository}.
 *
 * Storage model: one {@link CaPackEntity} row per pack, with nested DTOs
 * serialised to CLOB columns via the injected {@link ObjectMapper}. Tool
 * outputs are stored as a JSON array alongside the pack. Full
 * serialise/deserialise round-trip covers everything the pipeline and
 * replay endpoint need without any normalised child tables.
 *
 * Append-only guarantee (app-level): {@link #persistPack} throws
 * {@link IllegalStateException} if a row already exists for the packId.
 * Operator tooling can still override the DB row under authority.
 */
public class JpaPackRepository implements PackRepository {
    // non-final: Spring CGLIB subclasses this type to proxy @Transactional methods.

    private static final TypeReference<List<ToolOutputDto>>     LIST_TOOL_OUTPUTS     = new TypeReference<>() {};
    private static final TypeReference<List<ReviewerActionDto>> LIST_REVIEWER_ACTIONS = new TypeReference<>() {};
    private static final TypeReference<List<SignOffDto>>        LIST_SIGN_OFFS        = new TypeReference<>() {};

    private final CaPackJpaRepository caPackJpaRepository;
    private final ObjectMapper objectMapper;

    public JpaPackRepository(CaPackJpaRepository caPackJpaRepository, ObjectMapper objectMapper) {
        this.caPackJpaRepository = Objects.requireNonNull(caPackJpaRepository, "caPackJpaRepository");
        this.objectMapper        = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    @Transactional
    public void persistPack(CaPackDto pack, List<ToolOutputDto> toolOutputs) {
        Objects.requireNonNull(pack, "pack");
        List<ToolOutputDto> outputs = (toolOutputs == null) ? List.of() : toolOutputs;

        if (caPackJpaRepository.existsById(pack.packId())) {
            throw new IllegalStateException(
                    "Pack already persisted: packId=" + pack.packId() + " (append-only at application layer)");
        }

        CaPackEntity row = new CaPackEntity();
        row.setPackId(pack.packId());
        row.setPackVersion(pack.packVersion());
        row.setPartyId(pack.partyId());
        row.setStatus(pack.status());
        row.setCreatedBy(pack.createdBy());
        row.setCreatedAt(pack.createdAt());
        row.setChecklistVersion(pack.checklistVersion());
        row.setPromptVersion(pack.promptVersion());
        row.setModelId(pack.modelId());
        row.setModelVersion(pack.modelVersion());
        row.setToolOutputsHashRoot(pack.toolOutputsHashRoot());

        row.setPartyFactsJson(      writeJson(pack.partyFacts(),              "partyFacts"));
        row.setChecklistResultJson( writeJson(pack.checklistResult(),         "checklistResult"));
        row.setToolOutputsJson(     writeJson(outputs,                        "toolOutputs"));
        row.setRawAiOutputJson(     writeJsonNullable(pack.rawAiOutput(),       "rawAiOutput"));
        row.setValidationReportJson(writeJsonNullable(pack.validationReport(), "validationReport"));
        row.setFinalSummaryJson(    writeJsonNullable(pack.finalEditedSummary(),"finalSummary"));
        row.setReviewerActionsJson( writeJson(pack.reviewerActions(),         "reviewerActions"));
        row.setSignOffChainJson(    writeJson(pack.signOffChain(),            "signOffChain"));

        caPackJpaRepository.save(row);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaPackDto> findById(String packId) {
        Objects.requireNonNull(packId, "packId");
        return caPackJpaRepository.findById(packId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ToolOutputDto> findToolOutputsByPackId(String packId) {
        Objects.requireNonNull(packId, "packId");
        return caPackJpaRepository.findById(packId)
                .map(row -> readJson(row.getToolOutputsJson(), LIST_TOOL_OUTPUTS, "toolOutputs"))
                .orElseGet(List::of);
    }

    // ---- entity -> DTO ----

    private CaPackDto toDto(CaPackEntity row) {
        PartyFactsDto       partyFacts       = readJson(row.getPartyFactsJson(),      PartyFactsDto.class,       "partyFacts");
        ChecklistResultDto  checklistResult  = readJson(row.getChecklistResultJson(), ChecklistResultDto.class,  "checklistResult");
        RawAiOutputDto      rawAiOutput      = readJsonNullable(row.getRawAiOutputJson(),      RawAiOutputDto.class,      "rawAiOutput");
        ValidationReportDto validationReport = readJsonNullable(row.getValidationReportJson(), ValidationReportDto.class, "validationReport");
        FinalSummaryDto     finalSummary     = readJsonNullable(row.getFinalSummaryJson(),     FinalSummaryDto.class,     "finalSummary");
        List<ReviewerActionDto> reviewerActions = readJson(row.getReviewerActionsJson(), LIST_REVIEWER_ACTIONS, "reviewerActions");
        List<SignOffDto>        signOffChain    = readJson(row.getSignOffChainJson(),    LIST_SIGN_OFFS,        "signOffChain");

        return new CaPackDto(
                row.getPackId(),
                row.getPackVersion(),
                row.getPartyId(),
                row.getStatus(),
                row.getCreatedBy(),
                row.getCreatedAt(),
                row.getChecklistVersion(),
                row.getPromptVersion(),
                row.getModelId(),
                row.getModelVersion(),
                row.getToolOutputsHashRoot(),
                partyFacts,
                checklistResult,
                rawAiOutput,
                validationReport,
                finalSummary,
                reviewerActions,
                signOffChain);
    }

    // ---- JSON helpers ----

    private String writeJson(Object value, String what) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise " + what + " to JSON", e);
        }
    }

    private String writeJsonNullable(Object value, String what) {
        return (value == null) ? null : writeJson(value, what);
    }

    private <T> T readJson(String json, Class<T> type, String what) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise " + what + " from JSON", e);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, String what) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise " + what + " from JSON", e);
        }
    }

    private <T> T readJsonNullable(String json, Class<T> type, String what) {
        if (json == null || json.isBlank()) return null;
        return readJson(json, type, what);
    }
}
