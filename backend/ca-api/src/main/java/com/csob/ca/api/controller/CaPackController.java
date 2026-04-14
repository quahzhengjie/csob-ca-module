package com.csob.ca.api.controller;

import com.csob.ca.orchestration.PipelineCoordinator;
import com.csob.ca.shared.dto.CaPackDto;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controllers depend ONLY on ca-orchestration + ca-shared.
 * Direct access to repositories or entities is forbidden (ArchUnit TODO).
 */
@RestController
@RequestMapping("/api/ca/packs")
public class CaPackController {

    private final PipelineCoordinator pipelineCoordinator;

    public CaPackController(PipelineCoordinator pipelineCoordinator) {
        this.pipelineCoordinator = pipelineCoordinator;
    }

    @PostMapping
    public ResponseEntity<CaPackDto> createPack(@RequestParam("partyId") String partyId,
                                                @RequestParam("checklistVersion") String checklistVersion,
                                                @RequestParam("promptVersion") String promptVersion) {
        throw new UnsupportedOperationException(
                "Skeleton — allocate packId, invoke pipelineCoordinator.run, return 201.");
    }

    @GetMapping("/{packId}")
    public ResponseEntity<CaPackDto> getPack(@PathVariable String packId) {
        throw new UnsupportedOperationException(
                "Skeleton — call a read-side service (to be added in ca-orchestration) " +
                "and return the CaPackDto. Controllers must NOT touch repositories.");
    }
}
