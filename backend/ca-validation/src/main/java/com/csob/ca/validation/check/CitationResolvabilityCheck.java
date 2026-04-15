package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.CitationDto;
import com.csob.ca.shared.dto.SectionDto;
import com.csob.ca.shared.dto.SentenceDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.dto.ValidationFailureDto;
import com.csob.ca.shared.dto.tool.DocumentMetadataToolPayload;
import com.csob.ca.shared.dto.tool.PartyToolPayload;
import com.csob.ca.shared.dto.tool.RelatedPartiesToolPayload;
import com.csob.ca.shared.dto.tool.ScreeningToolPayload;
import com.csob.ca.shared.dto.tool.ToolPayload;
import com.csob.ca.shared.enums.CheckStatus;
import com.csob.ca.shared.enums.SourceType;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;
import com.csob.ca.validation.support.CitationResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Third validation check. Asserts every citation's sourceType + sourceId
 * points to an entity that exists in this pack's frozen evidence
 * (ToolOutputs + ChecklistResult). Does NOT walk the fieldPath — that is
 * FACT_GROUNDING's responsibility and is not implemented yet.
 *
 * Per-sourceType resolution:
 *
 *   CHECKLIST_FINDING → sourceId ≡ some ChecklistFindingDto.ruleId in
 *                        context.checklistResult().findings()
 *   DOCUMENT_META     → sourceId ≡ some DocumentMetaDto.documentId in
 *                        any DocumentMetadataToolPayload in context.toolOutputs()
 *   PARTY_FIELD       → sourceId ≡ the PartyCoreDto.partyId inside the
 *                        PartyToolPayload in context.toolOutputs()
 *   SCREENING_RESULT  → sourceId ≡ some ScreeningResultDto.resultId in
 *                        any ScreeningToolPayload in context.toolOutputs()
 *   RELATED_PARTY     → sourceId ≡ some RelatedPartyDto.relatedPartyId in
 *                        any RelatedPartiesToolPayload in context.toolOutputs()
 *
 * Reads only the typed AiSummaryPayloadDto bound by SchemaValidator — never
 * re-parses rawJsonText. Never throws.
 *
 * Contract notes:
 *  - The enum value is {@link ValidationCheckId#CITATION_RESOLVABILITY}
 *    (existing ca-shared contract). Your task spec called this
 *    "CITATION_RESOLVABLE" — same semantics, no rename was applied to
 *    avoid modifying ca-shared.
 *  - ValidationFailureDto uses `locator` (JSON Pointer) and `detail`
 *    (message) per the existing DTO contract.
 *  - {@link CitationResolver} is kept on the constructor for wiring stability
 *    and is reserved for FACT_GROUNDING (which will walk fieldPath to a
 *    value). It is deliberately unused here — resolvability is the narrower
 *    question of whether the cited entity exists at all.
 */
public final class CitationResolvabilityCheck implements ValidationCheck {

    static final String CODE_SCHEMA_NOT_PASSED     = "SCHEMA_NOT_PASSED";
    static final String CODE_CITATION_UNRESOLVABLE = "CITATION_UNRESOLVABLE";

    private static final String UNRESOLVABLE_MSG = "Citation cannot be resolved";

    @SuppressWarnings("unused") // reserved for FACT_GROUNDING
    private final CitationResolver resolver;

    public CitationResolvabilityCheck(CitationResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.CITATION_RESOLVABILITY;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        AiSummaryPayloadDto payload = (context == null) ? null : context.parsedPayload();
        if (payload == null) {
            return singleFail(CODE_SCHEMA_NOT_PASSED, "/",
                    "parsedPayload is null — SCHEMA must pass before CITATION_RESOLVABILITY runs");
        }

        List<ToolOutputDto> toolOutputs = (context.toolOutputs() == null)
                ? List.of() : context.toolOutputs();
        ChecklistResultDto checklistResult = context.checklistResult();

        List<SectionDto> sections = payload.sections();
        List<ValidationFailureDto> failures = new ArrayList<>();

        for (int si = 0; si < sections.size(); si++) {
            SectionDto section = sections.get(si);
            if (section == null) continue;
            List<SentenceDto> sentences = section.sentences();
            if (sentences == null) continue;

            for (int ti = 0; ti < sentences.size(); ti++) {
                SentenceDto sentence = sentences.get(ti);
                if (sentence == null) continue;
                List<CitationDto> citations = sentence.citations();
                if (citations == null) continue;

                for (int ci = 0; ci < citations.size(); ci++) {
                    CitationDto citation = citations.get(ci);
                    if (isResolvable(citation, toolOutputs, checklistResult)) continue;
                    failures.add(new ValidationFailureDto(
                            CODE_CITATION_UNRESOLVABLE,
                            "/sections/" + si + "/sentences/" + ti + "/citations/" + ci,
                            UNRESOLVABLE_MSG));
                }
            }
        }

        if (failures.isEmpty()) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.CITATION_RESOLVABILITY, CheckStatus.PASS, List.of());
        }
        return new ValidationCheckResultDto(
                ValidationCheckId.CITATION_RESOLVABILITY, CheckStatus.FAIL, failures);
    }

    // ---- resolution ----

    private static boolean isResolvable(CitationDto citation,
                                        List<ToolOutputDto> toolOutputs,
                                        ChecklistResultDto checklistResult) {
        if (citation == null) return false;
        SourceType type = citation.sourceType();
        String sourceId = citation.sourceId();
        if (type == null || sourceId == null || sourceId.isEmpty()) return false;

        return switch (type) {
            case CHECKLIST_FINDING -> {
                if (checklistResult == null || checklistResult.findings() == null) {
                    yield false;
                }
                yield checklistResult.findings().stream()
                        .anyMatch(f -> sourceId.equals(f.ruleId()));
            }
            case DOCUMENT_META -> payloads(toolOutputs, DocumentMetadataToolPayload.class)
                    .flatMap(p -> p.documents().stream())
                    .anyMatch(d -> sourceId.equals(d.documentId()));
            case SCREENING_RESULT -> payloads(toolOutputs, ScreeningToolPayload.class)
                    .flatMap(p -> p.results().stream())
                    .anyMatch(r -> sourceId.equals(r.resultId()));
            case RELATED_PARTY -> payloads(toolOutputs, RelatedPartiesToolPayload.class)
                    .flatMap(p -> p.relatedParties().stream())
                    .anyMatch(r -> sourceId.equals(r.relatedPartyId()));
            case PARTY_FIELD -> payloads(toolOutputs, PartyToolPayload.class)
                    .map(PartyToolPayload::party)
                    .anyMatch(p -> sourceId.equals(p.partyId()));
        };
    }

    private static <T extends ToolPayload> Stream<T> payloads(List<ToolOutputDto> outputs,
                                                              Class<T> type) {
        return outputs.stream()
                .map(ToolOutputDto::payload)
                .filter(type::isInstance)
                .map(type::cast);
    }

    private static ValidationCheckResultDto singleFail(String code, String locator, String detail) {
        return new ValidationCheckResultDto(
                ValidationCheckId.CITATION_RESOLVABILITY,
                CheckStatus.FAIL,
                List.of(new ValidationFailureDto(code, locator, detail)));
    }
}
