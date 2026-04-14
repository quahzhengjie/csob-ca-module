package com.csob.ca.orchestration.policy;

/**
 * Retry policy for the InvokeAiStep. Default: NO retries in v1.
 * Retries are bounded, deterministic, and only applicable to the single
 * AI call. No retries for checklist, tools, validation, or persistence steps.
 */
public record RetryPolicy(boolean enabled, int maxAttempts) {

    public static RetryPolicy disabled() {
        return new RetryPolicy(false, 1);
    }

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (enabled && maxAttempts > 3) {
            throw new IllegalArgumentException("v1 caps retries at 3");
        }
    }
}
