package com.csob.ca.orchestration.exception;

public class PipelineException extends RuntimeException {

    private final String packId;
    private final String stepName;

    public PipelineException(String packId, String stepName, String message) {
        super(message);
        this.packId = packId;
        this.stepName = stepName;
    }

    public PipelineException(String packId, String stepName, String message, Throwable cause) {
        super(message, cause);
        this.packId = packId;
        this.stepName = stepName;
    }

    public String packId() { return packId; }

    public String stepName() { return stepName; }
}
