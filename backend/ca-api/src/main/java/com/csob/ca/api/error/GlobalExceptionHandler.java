package com.csob.ca.api.error;

import com.csob.ca.orchestration.exception.PipelineException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to RFC 7807 problem+json responses.
 *
 * Stack traces are NEVER returned to clients — only stable error codes,
 * the failing step name (where applicable), and the message. The full
 * trace is logged at server side for diagnostics.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PipelineException.class)
    public ResponseEntity<ProblemDetail> handlePipeline(PipelineException ex) {
        log.error("Pipeline failure for packId={} step={}", ex.packId(), ex.stepName(), ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        body.setTitle("Pipeline step failed");
        body.setProperty("code", "PIPELINE_STEP_FAILED");
        body.setProperty("packId", ex.packId());
        body.setProperty("stepName", ex.stepName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state", ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() == null ? "Illegal state" : ex.getMessage());
        body.setTitle("Internal server error");
        body.setProperty("code", "ILLEGAL_STATE");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage() == null ? "Bad request" : ex.getMessage());
        body.setTitle("Bad request");
        body.setProperty("code", "BAD_REQUEST");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
