package com.csob.ca.ai.provider;

import com.csob.ca.ai.ModelClient;
import com.csob.ca.ai.request.AiRequest;
import com.csob.ca.ai.request.AiResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Provider-agnostic HTTP ModelClient. POSTs a serialised {@link AiRequest}
 * as the request body and returns the response body verbatim as the
 * {@code rawJsonText} on {@link AiResponse}. Does NOT parse or validate
 * the response body — downstream ValidationPipeline owns that.
 *
 * Wire contract (client → server):
 *   POST {endpoint}
 *   Content-Type: application/json
 *   Accept:       application/json
 *   X-Pack-Id:    {request.packId()}
 *   Authorization: Bearer {authToken}       (if configured)
 *
 *   body: {
 *     "packId":           "...",
 *     "promptVersion":    "...",
 *     "systemPrompt":     "...",
 *     "userPrompt":       "...",
 *     "outputSchemaJson": "..."
 *   }
 *
 * Wire contract (server → client):
 *   HTTP 2xx with Content-Type: application/json
 *   Optional response headers:
 *     X-Model-Id:      {modelId}
 *     X-Model-Version: {modelVersion}
 *   body: the model's CASummary JSON, conforming to ai-output.schema.json.
 *
 * No OpenAI, Anthropic, or Bedrock field names on the wire. Adapting to a
 * specific provider is a separate proxy / adapter service's responsibility.
 *
 * Retry policy:
 *   - retries only on HttpTimeoutException, other IOException, or HTTP 5xx
 *   - does NOT retry 4xx (treated as malformed request — fail fast)
 *   - maxAttempts is inclusive; {@code maxAttempts=2} means one retry
 *   - no backoff in v1 (deterministic, small attempt count)
 *
 * Failure mode:
 *   - any unrecoverable condition throws {@link HttpModelClientException}
 *     (unchecked). InvokeAiStep / PipelineCoordinator is responsible for
 *     catching and translating to PipelineException.
 */
public final class HttpModelClient implements ModelClient {

    private static final Logger log = LoggerFactory.getLogger(HttpModelClient.class);

    private static final String HDR_CONTENT_TYPE   = "Content-Type";
    private static final String HDR_ACCEPT         = "Accept";
    private static final String HDR_AUTHORIZATION  = "Authorization";
    private static final String HDR_X_PACK_ID      = "X-Pack-Id";
    private static final String HDR_X_MODEL_ID     = "X-Model-Id";
    private static final String HDR_X_MODEL_VER    = "X-Model-Version";
    private static final String MIME_JSON          = "application/json";

    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration requestTimeout;
    private final int maxAttempts;
    /** Nullable / blank = no Authorization header sent. */
    private final String authToken;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public HttpModelClient(HttpClient httpClient,
                           URI endpoint,
                           Duration requestTimeout,
                           int maxAttempts,
                           String authToken,
                           ObjectMapper objectMapper) {
        this(httpClient, endpoint, requestTimeout, maxAttempts, authToken, objectMapper, Clock.systemUTC());
    }

    public HttpModelClient(HttpClient httpClient,
                           URI endpoint,
                           Duration requestTimeout,
                           int maxAttempts,
                           String authToken,
                           ObjectMapper objectMapper,
                           Clock clock) {
        this.httpClient     = Objects.requireNonNull(httpClient, "httpClient");
        this.endpoint       = Objects.requireNonNull(endpoint, "endpoint");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("maxAttempts must be in [1, 3]; got " + maxAttempts);
        }
        this.maxAttempts  = maxAttempts;
        this.authToken    = authToken;                     // nullable
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock        = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AiResponse invoke(AiRequest request) {
        Objects.requireNonNull(request, "request");
        String bodyJson = serialise(request);
        HttpRequest httpRequest = buildHttpRequest(request.packId(), bodyJson);

        Throwable lastFault = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(
                        httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return toAiResponse(request, response);
                }

                boolean transient5xx = status >= 500 && status <= 599;
                if (transient5xx && attempt < maxAttempts) {
                    log.warn("HttpModelClient attempt {} got HTTP {} — retrying", attempt, status);
                    lastFault = new HttpModelClientException("HTTP " + status);
                    continue;
                }
                // 4xx (caller bug / malformed request) or 5xx on last attempt.
                throw new HttpModelClientException(
                        "HTTP " + status + " from " + endpoint + " (attempt " + attempt + "/" + maxAttempts + ")");

            } catch (HttpTimeoutException toe) {
                lastFault = toe;
                if (attempt < maxAttempts) {
                    log.warn("HttpModelClient attempt {} timed out after {} — retrying",
                            attempt, requestTimeout);
                    continue;
                }
                throw new HttpModelClientException(
                        "Request timed out after " + maxAttempts + " attempts (timeout=" + requestTimeout + ")", toe);

            } catch (IOException ioe) {
                lastFault = ioe;
                if (attempt < maxAttempts) {
                    log.warn("HttpModelClient attempt {} network error — retrying: {}",
                            attempt, ioe.getMessage());
                    continue;
                }
                throw new HttpModelClientException(
                        "Network error after " + maxAttempts + " attempts", ioe);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new HttpModelClientException("Interrupted during HTTP send", ie);
            }
        }
        // Defensive: the loop above always either returns or throws.
        throw new HttpModelClientException("Exhausted retries without response", lastFault);
    }

    // ---- helpers ----

    private HttpRequest buildHttpRequest(String packId, String bodyJson) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(endpoint)
                .timeout(requestTimeout)
                .header(HDR_CONTENT_TYPE, MIME_JSON)
                .header(HDR_ACCEPT,       MIME_JSON)
                .header(HDR_X_PACK_ID,    packId)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8));
        if (authToken != null && !authToken.isBlank()) {
            b.header(HDR_AUTHORIZATION, "Bearer " + authToken);
        }
        return b.build();
    }

    private AiResponse toAiResponse(AiRequest request, HttpResponse<String> response) {
        String modelId      = response.headers().firstValue(HDR_X_MODEL_ID).orElse("http-model");
        String modelVersion = response.headers().firstValue(HDR_X_MODEL_VER).orElse("unknown");
        return new AiResponse(
                request.packId(),
                modelId,
                modelVersion,
                Instant.now(clock),
                response.body() == null ? "" : response.body());
    }

    private String serialise(AiRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new HttpModelClientException("Failed to serialise AiRequest to JSON", e);
        }
    }

    /**
     * Unchecked exception for HTTP-transport and wire failures. InvokeAiStep
     * is expected to catch this and translate to PipelineException (or
     * re-throw depending on RetryPolicy).
     */
    public static final class HttpModelClientException extends RuntimeException {
        public HttpModelClientException(String message) { super(message); }
        public HttpModelClientException(String message, Throwable cause) { super(message, cause); }
    }
}
