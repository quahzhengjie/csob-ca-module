package com.csob.ca.shared.dto;

import com.csob.ca.shared.enums.ReviewerRole;

import java.time.Instant;
import java.util.Objects;

public record SignOffDto(
        String signerUsername,
        ReviewerRole role,
        Instant signedAt,
        String attestationHash
) {
    public SignOffDto {
        Objects.requireNonNull(signerUsername, "signerUsername");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(signedAt, "signedAt");
        Objects.requireNonNull(attestationHash, "attestationHash");
    }
}
