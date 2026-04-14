package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;

/**
 * One atomic deterministic check. No model calls. No I/O beyond reading
 * configured policy files loaded at construction.
 */
public interface ValidationCheck {

    ValidationCheckId id();

    ValidationCheckResultDto check(ValidationContext context);
}
