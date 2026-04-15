package com.csob.ca.api.controller;

import com.csob.ca.orchestration.PipelineCoordinator;
import com.csob.ca.persistence.audit.AuditReader;
import com.csob.ca.persistence.repository.PackRepository;
import com.csob.ca.shared.dto.AddressDto;
import com.csob.ca.shared.dto.AuditEventDto;
import com.csob.ca.shared.dto.CaPackDto;
import com.csob.ca.shared.dto.IndividualDetailsDto;
import com.csob.ca.shared.dto.PartyFactsDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.ValidationReportDto;
import com.csob.ca.shared.enums.PartyType;
import com.csob.ca.validation.ValidationPipeline;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CA pack endpoints.
 *
 * Controllers depend on ca-orchestration + ca-validation + ca-shared +
 * ca-persistence (read-only port usage). Direct access to JPA entity
 * classes is forbidden (ArchUnit TODO).
 *
 * v1 placeholders (called out in-line, not part of the production contract):
 *   - packId is generated here. Persistence assigns it today on write.
 *   - partyFacts is synthesised from partyId alone. A real PartyTool
 *     adapter will replace this placeholder when Phase 3 lands.
 *   - createdBy is a placeholder. Will come from the authenticated
 *     principal via Spring Security when SecurityConfig is hardened.
 */
@RestController
@RequestMapping("/ca/packs")  // server.servlet.context-path=/api is prepended by Tomcat
public class CaPackController {

    private final PipelineCoordinator pipelineCoordinator;
    private final PackRepository packRepository;
    private final AuditReader auditReader;
    private final ValidationPipeline validationPipeline;
    private final Clock clock;

    public CaPackController(PipelineCoordinator pipelineCoordinator,
                            PackRepository packRepository,
                            AuditReader auditReader,
                            ValidationPipeline validationPipeline,
                            Clock clock) {
        this.pipelineCoordinator = pipelineCoordinator;
        this.packRepository      = packRepository;
        this.auditReader         = auditReader;
        this.validationPipeline  = validationPipeline;
        this.clock               = clock;
    }

    @PostMapping
    public ResponseEntity<CaPackDto> createPack(@RequestParam("partyId") String partyId,
                                                @RequestParam("checklistVersion") String checklistVersion,
                                                @RequestParam("promptVersion") String promptVersion) {
        String packId = newPackId();
        PartyFactsDto partyFacts = synthesisePartyFacts(partyId);   // v1 placeholder
        String createdBy = "pipeline";                              // v1 placeholder

        CaPackDto pack = pipelineCoordinator.run(
                packId, partyId, checklistVersion, promptVersion, partyFacts, createdBy);

        // Persistence wrote the pack inside PersistStep; return 201 with a Location.
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .header("Location", "/api/ca/packs/" + pack.packId())
                .body(pack);
    }

    @GetMapping("/{packId}")
    public ResponseEntity<CaPackDto> getPack(@PathVariable String packId) {
        return packRepository.findById(packId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Replay endpoint: re-runs ONLY the validation pipeline against the
     * raw AI output stored with the pack. Does NOT re-call the model;
     * does NOT re-call tools. Returns a fresh {@link ValidationReportDto}.
     *
     * Use case: confirm that a historical pack is still VALID under the
     * current validation-checks / schema / banned-vocabulary configuration.
     */
    @PostMapping("/{packId}/replay")
    public ResponseEntity<ValidationReportDto> replayValidation(@PathVariable String packId) {
        Optional<CaPackDto> loaded = packRepository.findById(packId);
        if (loaded.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CaPackDto pack = loaded.get();
        if (pack.rawAiOutput() == null) {
            // Persisted pack has no AI output to replay against (would only
            // happen if a future pipeline run left it null, which today's
            // InvokeAiStep always avoids via the synthetic MALFORMED fallback).
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        List<ToolOutputDto> toolOutputs = packRepository.findToolOutputsByPackId(packId);
        ValidationReportDto fresh = validationPipeline.validate(
                pack.packId(),
                pack.checklistVersion(),
                toolOutputs,
                pack.checklistResult(),
                pack.rawAiOutput());

        return ResponseEntity.ok(fresh);
    }

    /**
     * Read-only audit trail for a pack. Returns the per-step events
     * emitted by PersistStep in append order (oldest → newest). The hash
     * chain values are intentionally NOT exposed by this endpoint; they
     * remain inside ca-persistence and can be re-derived for verification.
     */
    @GetMapping("/{packId}/audit")
    public ResponseEntity<List<AuditEventDto>> getAuditTrail(@PathVariable String packId) {
        if (packRepository.findById(packId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(auditReader.findEventsByPackId(packId));
    }

    // ---- v1 placeholders (do NOT rely on in tests / production) ----

    private String newPackId() {
        // Schema requires ^[A-Za-z0-9_-]{8,64}$. UUID prefix satisfies that.
        return "pack-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * v1 placeholder — returns a minimal, deterministic PartyFactsDto from
     * the partyId alone. Real values will come from a PartyTool adapter.
     */
    private PartyFactsDto synthesisePartyFacts(String partyId) {
        return new PartyFactsDto(
                partyId,
                PartyType.INDIVIDUAL,
                "Placeholder Name",
                List.of(),
                "SG",
                List.of(),
                new AddressDto("Unknown", null, "Unknown", null, null, "SG"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new IndividualDetailsDto(LocalDate.of(1970, 1, 1), List.of("SG")),
                null,
                Instant.now(clock),
                "placeholder-v1");
    }
}
