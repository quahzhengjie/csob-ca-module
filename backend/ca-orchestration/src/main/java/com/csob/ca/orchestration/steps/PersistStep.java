package com.csob.ca.orchestration.steps;

import com.csob.ca.persistence.audit.AuditWriter;
import com.csob.ca.persistence.repository.PackRepository;

/**
 * Final step: writes the immutable CaPack snapshot + audit event. Runs once
 * per successful pipeline (even when validation REJECTED, so the REJECTED
 * report is persisted alongside the raw AI output for audit).
 */
public final class PersistStep implements PipelineStep {

    private final PackRepository packRepository;
    private final AuditWriter auditWriter;

    public PersistStep(PackRepository packRepository, AuditWriter auditWriter) {
        this.packRepository = packRepository;
        this.auditWriter = auditWriter;
    }

    @Override
    public String name() {
        return "PERSIST";
    }

    @Override
    public void execute(StepContext context) {
        throw new UnsupportedOperationException(
                "Skeleton — build CaPackDto from context, call packRepository.persistPack, " +
                "and auditWriter.recordEvent for the PIPELINE_COMPLETED audit event.");
    }
}
