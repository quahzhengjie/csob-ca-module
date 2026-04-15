package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.dto.ValidationFailureDto;
import com.csob.ca.shared.enums.CheckStatus;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Binds the AI output to the pack and checklist version the pipeline is
 * currently validating. Rejects the output if either identifier drifts —
 * for example, a cached response from a different pack, or a model call
 * made against an older checklist revision.
 *
 * Reads only the typed AiSummaryPayloadDto that SchemaValidator bound onto
 * the ValidationContext. Never re-parses rawJsonText. Never throws.
 *
 * Failure codes:
 *   SCHEMA_NOT_PASSED           — parsedPayload is null (SCHEMA didn't pass upstream)
 *   PACK_ID_MISMATCH            — payload.packId != pipelinePackId
 *   CHECKLIST_VERSION_MISMATCH  — payload.checklistVersion != pipelineChecklistVersion
 *
 * Both ID mismatches are collected in a single run (no early-exit on the
 * first mismatch) so the reviewer sees the full picture.
 */
public final class PackBindingCheck implements ValidationCheck {

    static final String CODE_SCHEMA_NOT_PASSED          = "SCHEMA_NOT_PASSED";
    static final String CODE_PACK_ID_MISMATCH           = "PACK_ID_MISMATCH";
    static final String CODE_CHECKLIST_VERSION_MISMATCH = "CHECKLIST_VERSION_MISMATCH";

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.PACK_BINDING;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        AiSummaryPayloadDto payload = (context == null) ? null : context.parsedPayload();
        if (payload == null) {
            return singleFail(CODE_SCHEMA_NOT_PASSED, "/",
                    "parsedPayload is null — SCHEMA must pass before PACK_BINDING runs");
        }

        List<ValidationFailureDto> failures = new ArrayList<>();

        String expectedPackId = context.pipelinePackId();
        String actualPackId   = payload.packId();
        if (!Objects.equals(expectedPackId, actualPackId)) {
            failures.add(new ValidationFailureDto(
                    CODE_PACK_ID_MISMATCH,
                    "/packId",
                    "Pack ID mismatch: expected='" + render(expectedPackId)
                            + "', actual='" + render(actualPackId) + "'"));
        }

        String expectedVersion = context.pipelineChecklistVersion();
        String actualVersion   = payload.checklistVersion();
        if (!Objects.equals(expectedVersion, actualVersion)) {
            failures.add(new ValidationFailureDto(
                    CODE_CHECKLIST_VERSION_MISMATCH,
                    "/checklistVersion",
                    "Checklist version mismatch: expected='" + render(expectedVersion)
                            + "', actual='" + render(actualVersion) + "'"));
        }

        if (failures.isEmpty()) {
            return new ValidationCheckResultDto(
                    ValidationCheckId.PACK_BINDING, CheckStatus.PASS, List.of());
        }
        return new ValidationCheckResultDto(
                ValidationCheckId.PACK_BINDING, CheckStatus.FAIL, failures);
    }

    private static String render(String s) {
        return (s == null) ? "null" : s;
    }

    private static ValidationCheckResultDto singleFail(String code, String locator, String detail) {
        return new ValidationCheckResultDto(
                ValidationCheckId.PACK_BINDING,
                CheckStatus.FAIL,
                List.of(new ValidationFailureDto(code, locator, detail)));
    }
}
