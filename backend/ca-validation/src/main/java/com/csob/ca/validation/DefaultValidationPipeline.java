package com.csob.ca.validation;

import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.RawAiOutputDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.dto.ValidationFailureDto;
import com.csob.ca.shared.dto.ValidationReportDto;
import com.csob.ca.shared.enums.CheckStatus;
import com.csob.ca.shared.enums.ValidationStatus;
import com.csob.ca.validation.check.ValidationCheck;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-order composition of ValidationChecks.
 *
 * Wiring (implemented): runs every check, aggregates per-check results into
 * a ValidationReportDto, computes overall VALID / REJECTED.
 *
 * Individual check bodies (skeleton): throw UnsupportedOperationException until
 * implemented. The pipeline catches that deterministically and records a FAIL
 * with code NOT_IMPLEMENTED so the scaffold is exercisable end-to-end and the
 * report surface shows exactly what remains.
 *
 * Any other throwable from a check is recorded as CHECK_ERROR and treated as FAIL.
 * The pipeline itself never throws.
 */
public final class DefaultValidationPipeline implements ValidationPipeline {

    private final List<ValidationCheck> orderedChecks;
    private final Clock clock;

    /**
     * @param orderedChecks the fixed order:
     *                      SCHEMA,
     *                      PACK_BINDING,
     *                      SECTION_WHITELIST,
     *                      LENGTH_GUARD,
     *                      FORMAT_GUARD,
     *                      CITATION_PRESENCE,
     *                      CITATION_RESOLVABILITY,
     *                      FACT_GROUNDING,
     *                      COVERAGE,
     *                      BANNED_VOCABULARY
     */
    public DefaultValidationPipeline(List<ValidationCheck> orderedChecks) {
        this(orderedChecks, Clock.systemUTC());
    }

    public DefaultValidationPipeline(List<ValidationCheck> orderedChecks, Clock clock) {
        this.orderedChecks = List.copyOf(orderedChecks);
        this.clock = clock;
    }

    @Override
    public ValidationReportDto validate(String pipelinePackId,
                                        String pipelineChecklistVersion,
                                        List<ToolOutputDto> toolOutputs,
                                        ChecklistResultDto checklistResult,
                                        RawAiOutputDto rawAiOutput) {

        ValidationContext context = new ValidationContext(
                pipelinePackId,
                pipelineChecklistVersion,
                toolOutputs,
                checklistResult,
                rawAiOutput);

        List<ValidationCheckResultDto> results = new ArrayList<>(orderedChecks.size());
        boolean anyFail = false;

        for (ValidationCheck check : orderedChecks) {
            ValidationCheckResultDto result = runOne(check, context);
            results.add(result);
            if (result.status() == CheckStatus.FAIL) {
                anyFail = true;
            }
        }

        return new ValidationReportDto(
                pipelinePackId,
                clock.instant(),
                rawAiOutput.payloadHash(),
                anyFail ? ValidationStatus.REJECTED : ValidationStatus.VALID,
                results);
    }

    private ValidationCheckResultDto runOne(ValidationCheck check, ValidationContext context) {
        try {
            return check.check(context);
        } catch (UnsupportedOperationException notImplemented) {
            return new ValidationCheckResultDto(
                    check.id(),
                    CheckStatus.FAIL,
                    List.of(new ValidationFailureDto(
                            "NOT_IMPLEMENTED",
                            "/",
                            safeMessage(notImplemented, "check body is a skeleton"))));
        } catch (RuntimeException fault) {
            return new ValidationCheckResultDto(
                    check.id(),
                    CheckStatus.FAIL,
                    List.of(new ValidationFailureDto(
                            "CHECK_ERROR",
                            "/",
                            safeMessage(fault, "unexpected failure in check " + check.id()))));
        }
    }

    private static String safeMessage(Throwable t, String fallback) {
        String msg = t.getMessage();
        return (msg == null || msg.isBlank()) ? fallback : msg;
    }
}
