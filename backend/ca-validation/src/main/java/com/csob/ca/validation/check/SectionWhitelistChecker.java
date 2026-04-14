package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;

public final class SectionWhitelistChecker implements ValidationCheck {

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.SECTION_WHITELIST;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        throw new UnsupportedOperationException(
                "Skeleton — reject duplicate section headings; enum membership " +
                "already enforced by schema.");
    }
}
