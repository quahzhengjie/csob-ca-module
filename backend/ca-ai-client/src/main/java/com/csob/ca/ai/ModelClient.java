package com.csob.ca.ai;

import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.ai.request.AiResponse;

/**
 * Single, stateless model invocation. No tool-calling. No function-calling.
 * No memory. No chaining. One call per pack.
 *
 * Implementations must be swappable without touching ca-orchestration.
 * Implementations must capture modelId and modelVersion on the response
 * so the audit layer can pin them to the pack.
 */
public interface ModelClient {
    AiResponse invoke(AiRequest request);
}
