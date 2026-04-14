package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.ChecklistFindingDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.CitationDto;
import com.csob.ca.shared.dto.FactMentionDto;
import com.csob.ca.shared.dto.SectionDto;
import com.csob.ca.shared.dto.SentenceDto;
import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.dto.ValidationFailureDto;
import com.csob.ca.shared.enums.CheckStatus;
import com.csob.ca.shared.enums.RuleStatus;
import com.csob.ca.shared.enums.SourceType;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;
import com.csob.ca.validation.support.Tokeniser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fifth / final v1 validation check. Prevents the AI from summarising around
 * critical deterministic findings.
 *
 * v1 scope (narrow and strict):
 *   Every ChecklistFindingDto with status FAIL or MISSING must be cited by at
 *   least one FactMention whose citation points at that finding
 *   (sourceType = CHECKLIST_FINDING, sourceId = ruleId).
 *
 * Out of scope for v1 (deferred to v2):
 *   - coverage of critical PartyFacts fields
 *   - sentence-level presence (rather than just citation presence)
 *   - tokeniser-based entity/number/date coverage
 *
 * Reads only the typed AiSummaryPayloadDto bound by SchemaValidator and the
 * ChecklistResult on the context. Never re-parses rawJsonText. Never throws.
 *
 * Field-naming note: the {@link Tokeniser} dependency is retained on the
 * constructor for wiring stability and is reserved for v2's advanced
 * coverage (DATE/NUMBER/STATUS token enforcement inside sentence.text).
 * It is deliberately unused in v1.
 */
public final class CoverageChecker implements ValidationCheck {

    static final String CODE_SCHEMA_NOT_PASSED        = "SCHEMA_NOT_PASSED";
    static final String CODE_FACT_MISSING_FROM_SUMMARY = "FACT_MISSING_FROM_SUMMARY";

    private static final String MISSING_MSG =
            "Checklist finding not represented in summary";

    @SuppressWarnings("unused") // reserved for v2 advanced coverage
    private final Tokeniser tokeniser;

    public CoverageChecker(Tokeniser tokeniser) {
        this.tokeniser = tokeniser;
    }

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.COVERAGE;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        AiSummaryPayloadDto payload = (context == null) ? null : context.parsedPayload();
        if (payload == null) {
            return singleFail(CODE_SCHEMA_NOT_PASSED, "/",
                    "parsedPayload is null — SCHEMA must pass before COVERAGE runs");
        }

        ChecklistResultDto checklistResult = context.checklistResult();
        if (checklistResult == null || checklistResult.findings() == null) {
            // No authoritative findings to verify coverage against — vacuously satisfied.
            return new ValidationCheckResultDto(
                    ValidationCheckId.COVERAGE, CheckStatus.PASS, List.of());
        }

        Set<String> citedRuleIds = collectCitedRuleIds(payload);

        List<ValidationFailureDto> failures = new ArrayList<>();
        for (ChecklistFindingDto finding : checklistResult.findings()) {
            if (finding == null) continue;
            RuleStatus status = finding.status();
            if (status != RuleStatus.FAIL && status != RuleStatus.MISSING) continue;
            String ruleId = finding.ruleId();
            if (ruleId == null || ruleId.isEmpty()) continue;
            if (citedRuleIds.contains(ruleId)) continue;

            failures.add(new ValidationFailureDto(
                    CODE_FACT_MISSING_FROM_SUMMARY,
                    "/",
                    MISSING_MSG));
        }

        if (failures.isEmpty()) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.COVERAGE, CheckStatus.PASS, List.of());
        }
        return new ValidationCheckResultDto(
                ValidationCheckId.COVERAGE, CheckStatus.FAIL, failures);
    }

    // ---- helpers ----

    /**
     * Walks the AI payload once and collects every sourceId cited with
     * sourceType = CHECKLIST_FINDING. Only these are valid coverage
     * references; citations of other source types cannot discharge a
     * finding-coverage requirement even if the sourceId happens to collide.
     */
    private static Set<String> collectCitedRuleIds(AiSummaryPayloadDto payload) {
        Set<String> cited = new HashSet<>();
        List<SectionDto> sections = payload.sections();
        if (sections == null) return cited;

        for (SectionDto section : sections) {
            if (section == null) continue;
            List<SentenceDto> sentences = section.sentences();
            if (sentences == null) continue;
            for (SentenceDto sentence : sentences) {
                if (sentence == null) continue;
                List<FactMentionDto> mentions = sentence.factMentions();
                if (mentions == null) continue;
                for (FactMentionDto mention : mentions) {
                    if (mention == null) continue;
                    CitationDto citation = mention.citation();
                    if (citation == null) continue;
                    if (citation.sourceType() != SourceType.CHECKLIST_FINDING) continue;
                    String id = citation.sourceId();
                    if (id != null && !id.isEmpty()) {
                        cited.add(id);
                    }
                }
            }
        }
        return cited;
    }

    private static ValidationCheckResultDto singleFail(String code, String locator, String detail) {
        return new ValidationCheckResultDto(
                ValidationCheckId.COVERAGE,
                CheckStatus.FAIL,
                List.of(new ValidationFailureDto(code, locator, detail)));
    }
}
