package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.CitationDto;
import com.csob.ca.shared.dto.SectionDto;
import com.csob.ca.shared.dto.SentenceDto;
import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.dto.ValidationFailureDto;
import com.csob.ca.shared.enums.CheckStatus;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Second validation check. Enforces the "every sentence must be cited"
 * invariant by iterating the typed AiSummaryPayloadDto that SchemaValidator
 * bound onto the ValidationContext. Never re-parses JSON; never reads
 * rawJsonText.
 *
 * Failure codes:
 *  - SCHEMA_NOT_PASSED : parsedPayload is null (the SCHEMA check did not succeed)
 *  - CITATION_MISSING  : one or more sentences have a null or empty citations list
 *
 * Note (defence-in-depth): both the JSON Schema's {@code minItems: 1} and the
 * SentenceDto compact constructor already reject empty citation arrays.
 * This check formalises the invariant at the validation surface so audits
 * see it explicitly and any future weakening of either guard is caught here.
 *
 * Does NOT perform: citation resolvability, fact grounding, or coverage.
 *
 * Field naming note: the ValidationFailureDto contract uses `locator` for
 * the JSON Pointer path and `detail` for the message. This class populates
 * them per that contract.
 */
public final class CitationPresenceCheck implements ValidationCheck {

    static final String CODE_SCHEMA_NOT_PASSED = "SCHEMA_NOT_PASSED";
    static final String CODE_CITATION_MISSING  = "CITATION_MISSING";

    private static final String MISSING_MSG =
            "Sentence has no citations";

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.CITATION_PRESENCE;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        AiSummaryPayloadDto payload = (context == null) ? null : context.parsedPayload();
        if (payload == null) {
            return singleFail(CODE_SCHEMA_NOT_PASSED, "/",
                    "parsedPayload is null — SCHEMA must pass before CITATION_PRESENCE runs");
        }

        List<SectionDto> sections = payload.sections();
        if (sections == null) {
            return singleFail(CODE_CITATION_MISSING, "/sections",
                    "sections list is null");
        }

        List<ValidationFailureDto> failures = new ArrayList<>();

        for (int si = 0; si < sections.size(); si++) {
            SectionDto section = sections.get(si);
            List<SentenceDto> sentences = (section == null) ? null : section.sentences();
            if (sentences == null) {
                failures.add(new ValidationFailureDto(
                        CODE_CITATION_MISSING,
                        "/sections/" + si + "/sentences",
                        "sentences list is null"));
                continue;
            }
            for (int ti = 0; ti < sentences.size(); ti++) {
                SentenceDto sentence = sentences.get(ti);
                List<CitationDto> citations = (sentence == null) ? null : sentence.citations();
                if (citations == null || citations.isEmpty()) {
                    failures.add(new ValidationFailureDto(
                            CODE_CITATION_MISSING,
                            "/sections/" + si + "/sentences/" + ti,
                            MISSING_MSG));
                }
            }
        }

        if (failures.isEmpty()) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.CITATION_PRESENCE, CheckStatus.PASS, List.of());
        }
        return new ValidationCheckResultDto(
                ValidationCheckId.CITATION_PRESENCE, CheckStatus.FAIL, failures);
    }

    private static ValidationCheckResultDto singleFail(String code, String locator, String detail) {
        return new ValidationCheckResultDto(
                ValidationCheckId.CITATION_PRESENCE,
                CheckStatus.FAIL,
                List.of(new ValidationFailureDto(code, locator, detail)));
    }
}
