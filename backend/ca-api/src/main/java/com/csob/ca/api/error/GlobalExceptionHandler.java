package com.csob.ca.api.error;

import com.csob.ca.orchestration.exception.PipelineException;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to RFC 7807 problem+json responses. No leakage of
 * internal state (stack traces, entity types) into API bodies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PipelineException.class)
    public ResponseEntity<ProblemDetail> handlePipeline(PipelineException ex) {
        throw new UnsupportedOperationException(
                "Skeleton — return 500 problem+json with stepName, packId, " +
                "and a stable error code; never leak stack traces.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex) {
        throw new UnsupportedOperationException("Skeleton");
    }
}
