package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.CitationDto;
import com.csob.ca.shared.dto.FactMentionDto;
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
 * Defence-in-depth size caps over the AI payload, duplicating constraints
 * that the JSON Schema also enforces (maxItems, maxLength). Retained so
 * that any future weakening of the schema or the DTO contracts still gets
 * caught at the validation surface, and so the validation report explicitly
 * records which cap failed.
 *
 * Caps (match the JSON Schema):
 *   sections.size()               <= 5
 *   section.sentences.size()      <= 8
 *   sentence.text.length          <= 320
 *   sentence.citations.size()     <= 6
 *   sentence.factMentions.size()  <= 16
 *
 * All violations collected in one pass — no early exit. Never throws.
 */
public final class LengthGuardCheck implements ValidationCheck {

    public static final int MAX_SECTIONS_PER_PACK          = 5;
    public static final int MAX_SENTENCES_PER_SECTION      = 8;
    public static final int MAX_SENTENCE_CHARS             = 320;
    public static final int MAX_CITATIONS_PER_SENTENCE     = 6;
    public static final int MAX_FACT_MENTIONS_PER_SENTENCE = 16;

    static final String CODE_SCHEMA_NOT_PASSED           = "SCHEMA_NOT_PASSED";
    static final String CODE_SECTION_LIMIT_EXCEEDED      = "SECTION_LIMIT_EXCEEDED";
    static final String CODE_SENTENCE_LIMIT_EXCEEDED     = "SENTENCE_LIMIT_EXCEEDED";
    static final String CODE_TEXT_LENGTH_EXCEEDED        = "TEXT_LENGTH_EXCEEDED";
    static final String CODE_CITATION_LIMIT_EXCEEDED     = "CITATION_LIMIT_EXCEEDED";
    static final String CODE_FACT_MENTION_LIMIT_EXCEEDED = "FACT_MENTION_LIMIT_EXCEEDED";

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.LENGTH_GUARD;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        AiSummaryPayloadDto payload = (context == null) ? null : context.parsedPayload();
        if (payload == null) {
            return singleFail(CODE_SCHEMA_NOT_PASSED, "/",
                    "parsedPayload is null — SCHEMA must pass before LENGTH_GUARD runs");
        }

        List<SectionDto> sections = payload.sections();
        if (sections == null) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.LENGTH_GUARD, CheckStatus.PASS, List.of());
        }

        List<ValidationFailureDto> failures = new ArrayList<>();

        int sectionCount = sections.size();
        if (sectionCount > MAX_SECTIONS_PER_PACK) {
            failures.add(new ValidationFailureDto(
                    CODE_SECTION_LIMIT_EXCEEDED,
                    "/sections",
                    "Too many sections: actual=" + sectionCount
                            + ", allowed=" + MAX_SECTIONS_PER_PACK));
        }

        for (int si = 0; si < sectionCount; si++) {
            SectionDto section = sections.get(si);
            if (section == null) continue;

            List<SentenceDto> sentences = section.sentences();
            if (sentences == null) continue;

            int sentenceCount = sentences.size();
            if (sentenceCount > MAX_SENTENCES_PER_SECTION) {
                failures.add(new ValidationFailureDto(
                        CODE_SENTENCE_LIMIT_EXCEEDED,
                        "/sections/" + si + "/sentences",
                        "Too many sentences: actual=" + sentenceCount
                                + ", allowed=" + MAX_SENTENCES_PER_SECTION));
            }

            for (int ti = 0; ti < sentenceCount; ti++) {
                SentenceDto sentence = sentences.get(ti);
                if (sentence == null) continue;

                String text = sentence.text();
                if (text != null && text.length() > MAX_SENTENCE_CHARS) {
                    failures.add(new ValidationFailureDto(
                            CODE_TEXT_LENGTH_EXCEEDED,
                            "/sections/" + si + "/sentences/" + ti + "/text",
                            "Sentence text too long: actual=" + text.length()
                                    + ", allowed=" + MAX_SENTENCE_CHARS));
                }

                List<CitationDto> citations = sentence.citations();
                if (citations != null && citations.size() > MAX_CITATIONS_PER_SENTENCE) {
                    failures.add(new ValidationFailureDto(
                            CODE_CITATION_LIMIT_EXCEEDED,
                            "/sections/" + si + "/sentences/" + ti + "/citations",
                            "Too many citations: actual=" + citations.size()
                                    + ", allowed=" + MAX_CITATIONS_PER_SENTENCE));
                }

                List<FactMentionDto> mentions = sentence.factMentions();
                if (mentions != null && mentions.size() > MAX_FACT_MENTIONS_PER_SENTENCE) {
                    failures.add(new ValidationFailureDto(
                            CODE_FACT_MENTION_LIMIT_EXCEEDED,
                            "/sections/" + si + "/sentences/" + ti + "/factMentions",
                            "Too many factMentions: actual=" + mentions.size()
                                    + ", allowed=" + MAX_FACT_MENTIONS_PER_SENTENCE));
                }
            }
        }

        if (failures.isEmpty()) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.LENGTH_GUARD, CheckStatus.PASS, List.of());
        }
        return new ValidationCheckResultDto(
                ValidationCheckId.LENGTH_GUARD, CheckStatus.FAIL, failures);
    }

    private static ValidationCheckResultDto singleFail(String code, String locator, String detail) {
        return new ValidationCheckResultDto(
                ValidationCheckId.LENGTH_GUARD,
                CheckStatus.FAIL,
                List.of(new ValidationFailureDto(code, locator, detail)));
    }
}
