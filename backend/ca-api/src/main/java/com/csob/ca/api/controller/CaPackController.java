package com.csob.ca.api.controller;

import com.csob.ca.orchestration.PipelineCoordinator;
import com.csob.ca.shared.dto.AddressDto;
import com.csob.ca.shared.dto.CaPackDto;
import com.csob.ca.shared.dto.IndividualDetailsDto;
import com.csob.ca.shared.dto.PartyFactsDto;
import com.csob.ca.shared.enums.PartyType;

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
import java.util.UUID;

/**
 * CA pack endpoints. Controllers depend ONLY on ca-orchestration + ca-shared;
 * direct access to repositories or entities is forbidden (ArchUnit TODO).
 *
 * v1 placeholders (called out in-line, not part of the production contract):
 *   - packId is generated here. Will be assigned by persistence when that layer lands.
 *   - partyFacts is synthesised from partyId alone. Will be fetched by a
 *     real PartyTool adapter when Phase 3 lands.
 *   - createdBy is a placeholder. Will come from the authenticated principal
 *     via Spring Security when SecurityConfig is filled in.
 *   - GET /{packId} returns 501; no persistence yet, nothing to read back.
 */
@RestController
@RequestMapping("/api/ca/packs")
public class CaPackController {

    private final PipelineCoordinator pipelineCoordinator;
    private final Clock clock;

    public CaPackController(PipelineCoordinator pipelineCoordinator, Clock clock) {
        this.pipelineCoordinator = pipelineCoordinator;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<CaPackDto> createPack(@RequestParam("partyId") String partyId,
                                                @RequestParam("checklistVersion") String checklistVersion,
                                                @RequestParam("promptVersion") String promptVersion) {
        String packId = newPackId();
        PartyFactsDto partyFacts = synthesisePartyFacts(partyId);  // v1 placeholder
        String createdBy = "pipeline";                             // v1 placeholder

        CaPackDto pack = pipelineCoordinator.run(
                packId, partyId, checklistVersion, promptVersion, partyFacts, createdBy);

        // 200 rather than 201 for v1: nothing is persisted yet, so there's no
        // Location header to point to. Will become 201 when persistence lands.
        return ResponseEntity.status(HttpStatus.OK).body(pack);
    }

    @GetMapping("/{packId}")
    public ResponseEntity<CaPackDto> getPack(@PathVariable String packId) {
        // Read-side requires persistence (Phase 3). Return 501 rather than
        // fabricating data.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    // ---- v1 placeholders (do NOT rely on in tests / production) ----

    private String newPackId() {
        // Schema requires ^[A-Za-z0-9_-]{8,64}$. UUID hyphens satisfy that.
        return "pack-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * v1 placeholder — returns a minimal, deterministic PartyFactsDto from
     * the partyId alone. Real values will come from a PartyTool adapter.
     * Everything here is safe, non-PII, and clearly synthetic.
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
