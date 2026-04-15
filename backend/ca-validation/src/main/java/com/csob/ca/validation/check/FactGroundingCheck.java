package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.CitationDto;
import com.csob.ca.shared.dto.FactMentionDto;
import com.csob.ca.shared.dto.SectionDto;
import com.csob.ca.shared.dto.SentenceDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.dto.ValidationFailureDto;
import com.csob.ca.shared.enums.CheckStatus;
import com.csob.ca.shared.enums.FactKind;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;
import com.csob.ca.validation.support.CitationResolver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Fourth validation check. Confirms that every declared FactMention is
 * grounded in the resolved source value at the cited fieldPath.
 *
 * Delegates resolution to {@link CitationResolver}; does NOT duplicate
 * resolution logic and does NOT re-parse rawJsonText.
 *
 * Per-kind matching:
 *   IDENTIFIER : exact string equality
 *   DATE       : parse both sides to LocalDate (trying a small set of
 *                common formats) then compare temporally
 *   NUMBER     : strip comma separators, compare via BigDecimal numerically
 *   ENTITY     : trim + Locale.ROOT lowercase on both sides, then equals
 *   STATUS     : same as ENTITY (trim + case-insensitive)
 *
 * A FactMention is grounded iff the resolver returns a non-empty value AND
 * the value matches the claimed value under the kind-specific rule. Any
 * other outcome (unresolvable, normalisation failure, mismatch) is surfaced
 * as a FACT_NOT_GROUNDED failure at the mention's JSON Pointer.
 *
 * Never throws; continues processing all mentions before returning.
 */
public final class FactGroundingCheck implements ValidationCheck {

    static final String CODE_SCHEMA_NOT_PASSED = "SCHEMA_NOT_PASSED";
    static final String CODE_FACT_NOT_GROUNDED = "FACT_NOT_GROUNDED";

    private static final String NOT_GROUNDED_MSG = "Fact does not match source value";

    /** Ordered list of DATE formats tried by {@link #parseDate(String)}. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                              // 2026-04-14
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),     // 14 Apr 2026
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)     // 14 April 2026
    );

    private final CitationResolver resolver;

    public FactGroundingCheck(CitationResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.FACT_GROUNDING;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        AiSummaryPayloadDto payload = (context == null) ? null : context.parsedPayload();
        if (payload == null) {
            return singleFail(CODE_SCHEMA_NOT_PASSED, "/",
                    "parsedPayload is null — SCHEMA must pass before FACT_GROUNDING runs");
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
                List<FactMentionDto> mentions = sentence.factMentions();
                if (mentions == null) continue;

                for (int mi = 0; mi < mentions.size(); mi++) {
                    FactMentionDto mention = mentions.get(mi);
                    if (isGrounded(mention, toolOutputs, checklistResult)) continue;
                    failures.add(new ValidationFailureDto(
                            CODE_FACT_NOT_GROUNDED,
                            "/sections/" + si
                                    + "/sentences/" + ti
                                    + "/factMentions/" + mi,
                            NOT_GROUNDED_MSG));
                }
            }
        }

        if (failures.isEmpty()) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.FACT_GROUNDING, CheckStatus.PASS, List.of());
        }
        return new ValidationCheckResultDto(
                ValidationCheckId.FACT_GROUNDING, CheckStatus.FAIL, failures);
    }

    // ---- grounding test ----

    private boolean isGrounded(FactMentionDto mention,
                               List<ToolOutputDto> toolOutputs,
                               ChecklistResultDto checklistResult) {
        if (mention == null) return false;
        CitationDto citation = mention.citation();
        FactKind kind = mention.kind();
        String claimed = mention.value();
        if (citation == null || kind == null || claimed == null) return false;

        Optional<String> resolved;
        try {
            resolved = resolver.resolve(citation, toolOutputs, checklistResult);
        } catch (RuntimeException e) {
            return false;
        }
        if (resolved == null || resolved.isEmpty()) return false;   // null/absent source value → FAIL

        String source = resolved.get();
        return matches(kind, claimed, source);
    }

    // ---- matching / normalisation helpers ----

    static boolean matches(FactKind kind, String claimed, String source) {
        if (claimed == null || source == null) return false;
        try {
            return switch (kind) {
                case IDENTIFIER -> identifiersEqual(claimed, source);
                case DATE       -> datesEqual(claimed, source);
                case NUMBER     -> numbersEqual(claimed, source);
                case ENTITY     -> entitiesEqual(claimed, source);
                case STATUS     -> entitiesEqual(claimed, source);
            };
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** IDENTIFIER: exact string equality (no trim, no case fold). */
    static boolean identifiersEqual(String a, String b) {
        return a.equals(b);
    }

    /** DATE: parse both to LocalDate via the configured format list. */
    static boolean datesEqual(String a, String b) {
        LocalDate da = parseDate(a);
        LocalDate db = parseDate(b);
        return da != null && db != null && da.equals(db);
    }

    static LocalDate parseDate(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    /**
     * NUMBER: strip comma separators (handles English thousands grouping
     * like "1,000") and compare numerically via BigDecimal.
     */
    static boolean numbersEqual(String a, String b) {
        BigDecimal na = parseNumber(a);
        BigDecimal nb = parseNumber(b);
        return na != null && nb != null && na.compareTo(nb) == 0;
    }

    static BigDecimal parseNumber(String s) {
        if (s == null) return null;
        String cleaned = s.replace(",", "").trim();
        if (cleaned.isEmpty()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** ENTITY / STATUS: trim + Locale.ROOT lowercase, then equals. */
    static boolean entitiesEqual(String a, String b) {
        return normaliseEntity(a).equals(normaliseEntity(b));
    }

    static String normaliseEntity(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    // ---- failure factory ----

    private static ValidationCheckResultDto singleFail(String code, String locator, String detail) {
        return new ValidationCheckResultDto(
                ValidationCheckId.FACT_GROUNDING,
                CheckStatus.FAIL,
                List.of(new ValidationFailureDto(code, locator, detail)));
    }
}
