package com.csob.ca.ai.provider;

import com.csob.ca.ai.ModelClient;
import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.ai.request.AiResponse;

/**
 * Deterministic stub for local dev and tests. Returns a fixed, schema-shaped
 * response so the validation pipeline can be exercised without a real model.
 * Never used in staging or prod profiles.
 */
public final class StubModelClient implements ModelClient {

    @Override
    public AiResponse invoke(AiRequest request) {
        throw new UnsupportedOperationException(
                "Skeleton — return a canned, schema-valid AiResponse for packId=" + request.packId());
    }
}
