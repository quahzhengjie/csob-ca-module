package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;

/**
 * Rejects first-person markers, imperative-voice cues, and markdown leakage
 * in sentence text. Declarative restatement only.
 */
public final class FormatGuardChecker implements ValidationCheck {

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.FORMAT_GUARD;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        throw new UnsupportedOperationException(
                "Skeleton — detect first-person / imperative / markdown heuristics " +
                "in every sentence.text.");
    }
}
