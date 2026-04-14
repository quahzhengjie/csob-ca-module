package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;

public final class LengthGuardChecker implements ValidationCheck {

    public static final int MAX_SECTIONS_PER_PACK = 5;
    public static final int MAX_SENTENCES_PER_SECTION = 8;
    public static final int MAX_SENTENCE_CHARS = 320;

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.LENGTH_GUARD;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        throw new UnsupportedOperationException(
                "Skeleton — enforce caps as defence-in-depth; schema also enforces.");
    }
}
