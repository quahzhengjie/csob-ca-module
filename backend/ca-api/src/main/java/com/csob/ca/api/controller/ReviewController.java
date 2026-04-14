package com.csob.ca.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Review surface. Acknowledgement of FAIL/MISSING findings, MARK_REVIEWED,
 * and sign-off endpoints. Every action goes through a backend state-machine
 * guard before persisting.
 */
@RestController
@RequestMapping("/api/ca/packs/{packId}/review")
public class ReviewController {

    @PostMapping("/findings/{ruleId}/acknowledge")
    public ResponseEntity<Void> acknowledgeFinding(@PathVariable String packId,
                                                   @PathVariable String ruleId) {
        throw new UnsupportedOperationException(
                "Skeleton — dispatch to a review service on ca-orchestration.");
    }

    @PostMapping("/mark-reviewed")
    public ResponseEntity<Void> markReviewed(@PathVariable String packId) {
        throw new UnsupportedOperationException("Skeleton");
    }

    @PostMapping("/sign-off")
    public ResponseEntity<Void> signOff(@PathVariable String packId) {
        throw new UnsupportedOperationException(
                "Skeleton — 4-eyes enforcement: second signer must differ from the first.");
    }
}
