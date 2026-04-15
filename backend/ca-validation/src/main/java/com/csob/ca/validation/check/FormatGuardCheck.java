package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.AiSummaryPayloadDto;
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
 * Final validation check. Keeps sentence.text clean for downstream reading
 * and rendering: no newlines, no markdown smuggling, no surrounding
 * whitespace, no whitespace-only strings.
 *
 * Rules (applied to sentence.text only):
 *   - not null, not empty after trimming
 *   - no CR or LF characters anywhere in the string
 *   - no markdown-adjacent characters: '*', '_', '`' (covers '**', '__',
 *     and fenced '```' as well — detecting any single occurrence suffices)
 *   - leading and trailing whitespace is not allowed
 *
 * Reads only the typed AiSummaryPayloadDto bound by SchemaValidator onto
 * the ValidationContext. Never re-parses rawJsonText. Never throws.
 *
 * The JSON Schema already enforces minLength=1 and forbids newlines via
 * pattern; this check retains those rules as defence in depth and adds
 * the markdown and surrounding-whitespace constraints, which the schema
 * cannot express.
 *
 * All sentence violations are collected — no early exit.
 */
public final class FormatGuardCheck implements ValidationCheck {

    static final String CODE_SCHEMA_NOT_PASSED = "SCHEMA_NOT_PASSED";
    static final String CODE_INVALID_FORMAT    = "INVALID_FORMAT";

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.FORMAT_GUARD;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        AiSummaryPayloadDto payload = (context == null) ? null : context.parsedPayload();
        if (payload == null) {
            return singleFail(CODE_SCHEMA_NOT_PASSED, "/",
                    "parsedPayload is null — SCHEMA must pass before FORMAT_GUARD runs");
        }

        List<SectionDto> sections = payload.sections();
        if (sections == null) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.FORMAT_GUARD, CheckStatus.PASS, List.of());
        }

        List<ValidationFailureDto> failures = new ArrayList<>();

        for (int si = 0; si < sections.size(); si++) {
            SectionDto section = sections.get(si);
            if (section == null) continue;
            List<SentenceDto> sentences = section.sentences();
            if (sentences == null) continue;

            for (int ti = 0; ti < sentences.size(); ti++) {
                SentenceDto sentence = sentences.get(ti);
                if (sentence == null) continue;
                String locator = "/sections/" + si + "/sentences/" + ti;
                collectViolations(sentence.text(), locator, failures);
            }
        }

        if (failures.isEmpty()) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.FORMAT_GUARD, CheckStatus.PASS, List.of());
        }
        return new ValidationCheckResultDto(
                ValidationCheckId.FORMAT_GUARD, CheckStatus.FAIL, failures);
    }

    /**
     * Applies the full rule set to one sentence's text, appending one
     * {@link ValidationFailureDto} per violation. Short-circuits only when
     * further checks would be meaningless (null text, trimmed-to-empty).
     */
    private static void collectViolations(String text,
                                          String locator,
                                          List<ValidationFailureDto> failures) {
        if (text == null) {
            failures.add(fail(locator, "text is null"));
            return;
        }
        if (text.trim().isEmpty()) {
            failures.add(fail(locator, "empty text"));
            return;
        }
        if (!text.equals(text.trim())) {
            failures.add(fail(locator, "leading or trailing whitespace"));
        }
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            failures.add(fail(locator, "contains newline"));
        }
        if (text.indexOf('*') >= 0) {
            failures.add(fail(locator, "contains markdown '*'"));
        }
        if (text.indexOf('_') >= 0) {
            failures.add(fail(locator, "contains markdown '_'"));
        }
        if (text.indexOf('`') >= 0) {
            failures.add(fail(locator, "contains markdown '`'"));
        }
    }

    private static ValidationFailureDto fail(String locator, String detail) {
        return new ValidationFailureDto(CODE_INVALID_FORMAT, locator, detail);
    }

    private static ValidationCheckResultDto singleFail(String code, String locator, String detail) {
        return new ValidationCheckResultDto(
                ValidationCheckId.FORMAT_GUARD,
                CheckStatus.FAIL,
                List.of(new ValidationFailureDto(code, locator, detail)));
    }
}
