package com.csob.ca.orchestration.steps;

import com.csob.ca.persistence.audit.AuditWriter;
import com.csob.ca.persistence.repository.PackRepository;
import com.csob.ca.shared.dto.AuditEventDto;
import com.csob.ca.shared.dto.CaPackDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.RawAiOutputDto;
import com.csob.ca.shared.dto.ValidationReportDto;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Final pipeline step: writes the pack aggregate + emits the per-step
 * audit event trail.
 *
 * This step assembles a {@link CaPackDto} from the {@link StepContext}
 * using the same logic as {@link com.csob.ca.orchestration.PipelineCoordinator}
 * so the persisted pack is byte-identical to the pack returned by the
 * coordinator.
 *
 * Audit events emitted (order matters — the hash chain depends on it):
 *   1. INVOKE_TOOLS_COMPLETED
 *   2. EVALUATE_CHECKLIST_COMPLETED
 *   3. INVOKE_AI_COMPLETED
 *   4. VALIDATE_AI_COMPLETED
 *   5. PACK_CREATED
 *
 * Every event carries a stable, deterministic {@code detailsJson} derived
 * from the StepContext; the audit writer's SHA-256 chain therefore hashes
 * deterministically for a given pack run.
 *
 * Pipeline output contract: this step does NOT change what the coordinator
 * returns; the CaPackDto is built here for persistence, the coordinator
 * builds its own equivalent one for the return value. Both use the same
 * context and the same clock.
 */
public final class PersistStep implements PipelineStep {

    private static final String PLACEHOLDER_64_ZERO_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private final PackRepository packRepository;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public PersistStep(PackRepository packRepository, AuditWriter auditWriter, Clock clock) {
        this.packRepository = Objects.requireNonNull(packRepository, "packRepository");
        this.auditWriter    = Objects.requireNonNull(auditWriter, "auditWriter");
        this.clock          = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "PERSIST";
    }

    @Override
    public void execute(StepContext context) {
        Objects.requireNonNull(context, "context");

        CaPackDto pack = buildPack(context);

        // Single transaction around pack + audit would be ideal; for v1 each
        // call has its own @Transactional boundary (acceptable since the
        // audit hash chain is derivable from persisted events and a missing
        // audit row would be detectable on replay).
        packRepository.persistPack(pack, context.toolOutputs());

        Instant at = clock.instant();
        emitStepAudit(context, "INVOKE_TOOLS_COMPLETED",        buildInvokeToolsDetails(context), at);
        emitStepAudit(context, "EVALUATE_CHECKLIST_COMPLETED",  buildChecklistDetails(context),   at);
        emitStepAudit(context, "INVOKE_AI_COMPLETED",           buildAiDetails(context),          at);
        emitStepAudit(context, "VALIDATE_AI_COMPLETED",         buildValidationDetails(context),  at);
        emitStepAudit(context, "PACK_CREATED",                  buildPackCreatedDetails(pack),    at);
    }

    // ---- CaPackDto assembly (mirrors PipelineCoordinator#assemblePack) ----

    private CaPackDto buildPack(StepContext ctx) {
        RawAiOutputDto rawAi = ctx.rawAiOutput();
        ChecklistResultDto checklist = ctx.checklistResult();
        String modelId      = (rawAi != null) ? rawAi.modelId()      : "unavailable";
        String modelVersion = (rawAi != null) ? rawAi.modelVersion() : "unavailable";
        String hashRoot     = (checklist != null) ? checklist.toolOutputsHashRoot()
                                                  : PLACEHOLDER_64_ZERO_HASH;

        return new CaPackDto(
                ctx.packId(),
                1,
                ctx.partyId(),
                ctx.status(),
                "pipeline",
                clock.instant(),
                ctx.checklistVersion(),
                ctx.promptVersion(),
                modelId,
                modelVersion,
                hashRoot,
                ctx.partyFacts(),
                checklist,
                rawAi,
                ctx.validationReport(),
                null,
                List.of(),
                List.of());
    }

    // ---- audit event construction (deterministic detailsJson) ----

    private void emitStepAudit(StepContext ctx, String eventType, String details, Instant at) {
        auditWriter.recordEvent(new AuditEventDto(ctx.packId(), eventType, details, at));
    }

    private String buildInvokeToolsDetails(StepContext ctx) {
        return "{\"toolOutputCount\":" + ctx.toolOutputs().size() + "}";
    }

    private String buildChecklistDetails(StepContext ctx) {
        ChecklistResultDto r = ctx.checklistResult();
        if (r == null || r.completion() == null) return "{}";
        return "{\"checklistVersion\":\"" + escape(r.checklistVersion())
                + "\",\"total\":"   + r.completion().totalRules()
                + ",\"passed\":"    + r.completion().passed()
                + ",\"failed\":"    + r.completion().failed()
                + ",\"missing\":"   + r.completion().missing()
                + ",\"notApplicable\":" + r.completion().notApplicable()
                + "}";
    }

    private String buildAiDetails(StepContext ctx) {
        RawAiOutputDto r = ctx.rawAiOutput();
        if (r == null) return "{\"parseStatus\":\"ABSENT\"}";
        return "{\"modelId\":\"" + escape(r.modelId())
                + "\",\"modelVersion\":\"" + escape(r.modelVersion())
                + "\",\"parseStatus\":\""  + r.parseStatus() + "\"}";
    }

    private String buildValidationDetails(StepContext ctx) {
        ValidationReportDto r = ctx.validationReport();
        if (r == null) return "{\"status\":\"ABSENT\"}";
        int failedChecks = 0;
        for (var c : r.checks()) if (!c.failures().isEmpty()) failedChecks++;
        return "{\"status\":\"" + r.status()
                + "\",\"totalChecks\":" + r.checks().size()
                + ",\"failedChecks\":"  + failedChecks + "}";
    }

    private String buildPackCreatedDetails(CaPackDto pack) {
        return "{\"packId\":\"" + escape(pack.packId())
                + "\",\"partyId\":\"" + escape(pack.partyId())
                + "\",\"status\":\""  + pack.status() + "\"}";
    }

    private static String escape(String s) {
        return (s == null) ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
