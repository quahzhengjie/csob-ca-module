package com.csob.ca.persistence.entity;

import com.csob.ca.shared.enums.ReviewerActionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "csob_ca_reviewer_actions")
public class ReviewerActionEntity {

    @Id
    @Column(name = "action_id", nullable = false, length = 64)
    private String actionId;

    @Column(name = "pack_id", nullable = false, length = 64)
    private String packId;

    @Column(name = "reviewer_username", nullable = false, length = 128)
    private String reviewerUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private ReviewerActionType actionType;

    @Column(name = "target", length = 128)
    private String target;

    @Column(name = "acted_at", nullable = false)
    private Instant actedAt;

    @Column(name = "attestation_text", length = 1024)
    private String attestationText;

    protected ReviewerActionEntity() { /* JPA */ }
}
