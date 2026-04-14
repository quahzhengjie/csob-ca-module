package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.ReviewerActionType;

import java.time.Instant;
import java.util.Objects;

public record ReviewerActionDto(
        String actionId,
        String reviewerUsername,
        ReviewerActionType actionType,
        String target,
        Instant actedAt,
        String attestationText
) {
    public ReviewerActionDto {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(reviewerUsername, "reviewerUsername");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(actedAt, "actedAt");
        if (actionType == ReviewerActionType.ACKNOWLEDGE_FINDING && target == null) {
            throw new IllegalArgumentException(
                    "target (ruleId) required for ACKNOWLEDGE_FINDING");
        }
    }
}
