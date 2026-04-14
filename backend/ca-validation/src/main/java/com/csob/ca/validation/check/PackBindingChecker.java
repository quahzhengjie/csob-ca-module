package com.csob.ca.validation.check;

import com.csob.ca.shared.dto.ValidationCheckResultDto;
import com.csob.ca.shared.enums.ValidationCheckId;
import com.csob.ca.validation.ValidationContext;

/**
 * Rejects if the AI output's declared packId or checklistVersion does not
 * equal the pipeline's packId/checklistVersion. Prevents context drift
 * between the orchestrator and the model.
 */
public final class PackBindingChecker implements ValidationCheck {

    @Override
    public ValidationCheckId id() {
        return ValidationCheckId.PACK_BINDING;
    }

    @Override
    public ValidationCheckResultDto check(ValidationContext context) {
        throw new UnsupportedOperationException(
                "Skeleton — compare parsedPayload.packId / .checklistVersion " +
                "to context.pipelinePackId() / .pipelineChecklistVersion().");
    }
}
