package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.SectionDto;
import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.dto.ValidationFailureDto;
import com.csob.ca.shared.enums.CheckStatus;
import com.csob.ca.shared.enums.SectionHeading;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Enforces section-level structural invariants on the AI payload:
 *   - every section.heading is non-null
 *   - no two sections share the same heading
 *
 * The {@link SectionHeading} enum in ca-shared is closed, so enum-membership
 * is already guaranteed by the JSON Schema and by SectionDto's compact
 * constructor by the time parsedPayload exists. This check exists as
 * defence-in-depth (flags future DTO-contract weakening as null) and to
 * deduplicate headings — a semantic rule the JSON Schema cannot express.
 *
 * Reads only the typed AiSummaryPayloadDto bound by SchemaValidator onto
 * the ValidationContext. Never re-parses rawJsonText. Never throws.
 *
 * Failure codes:
 *   SCHEMA_NOT_PASSED          — parsedPayload is null
 *   INVALID_SECTION_HEADING    — heading is null (or the containing
 *                                 SectionDto is null)
 *   DUPLICATE_SECTION_HEADING  — heading appears in more than one section
 *
 * All invalid/duplicate headings are collected in one pass — no early exit.
 */
public final class SectionWhitelistCheck implements ValidationCheck {

    static final String CODE_SCHEMA_NOT_PASSED         = "SCHEMA_NOT_PASSED";
    static final String CODE_INVALID_SECTION_HEADING   = "INVALID_SECTION_HEADING";
    static final String CODE_DUPLICATE_SECTION_HEADING = "DUPLICATE_SECTION_HEADING";

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.SECTION_WHITELIST;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        AiSummaryPayloadDto payload = (context == null) ? null : context.parsedPayload();
        if (payload == null) {
            return singleFail(CODE_SCHEMA_NOT_PASSED, "/",
                    "parsedPayload is null — SCHEMA must pass before SECTION_WHITELIST runs");
        }

        List<SectionDto> sections = payload.sections();
        if (sections == null) {
            // Empty/absent sections do not violate this check's concern; other
            // checks (schema, length) surface structural issues.
            return new ValidationCheckResultDto(
                    ValidationCheckId.SECTION_WHITELIST, CheckStatus.PASS, List.of());
        }

        List<ValidationFailureDto> failures = new ArrayList<>();
        Map<SectionHeading, Integer> firstSeen = new EnumMap<>(SectionHeading.class);

        for (int si = 0; si < sections.size(); si++) {
            SectionDto section = sections.get(si);
            SectionHeading heading = (section == null) ? null : section.heading();
            String locator = "/sections/" + si + "/heading";

            if (heading == null) {
                failures.add(new ValidationFailureDto(
                        CODE_INVALID_SECTION_HEADING,
                        locator,
                        "Section heading is null"));
                continue;
            }

            Integer firstIndex = firstSeen.putIfAbsent(heading, si);
            if (firstIndex != null) {
                failures.add(new ValidationFailureDto(
                        CODE_DUPLICATE_SECTION_HEADING,
                        locator,
                        "Duplicate section heading '" + heading.name()
                                + "' (first seen at /sections/" + firstIndex + ")"));
            }
        }

        if (failures.isEmpty()) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.SECTION_WHITELIST, CheckStatus.PASS, List.of());
        }
        return new ValidationCheckResultDto(
                ValidationCheckId.SECTION_WHITELIST, CheckStatus.FAIL, failures);
    }

    private static ValidationCheckResultDto singleFail(String code, String locator, String detail) {
        return new ValidationCheckResultDto(
                ValidationCheckId.SECTION_WHITELIST,
                CheckStatus.FAIL,
                List.of(new ValidationFailureDto(code, locator, detail)));
    }
}
