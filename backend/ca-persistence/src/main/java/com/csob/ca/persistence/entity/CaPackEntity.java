package com.csob.ca.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.csob.ca.shared.enums.PackStatus;

import java.time.Instant;

@Entity
@Table(name = "csob_ca_packs")
public class CaPackEntity {

    @Id
    @Column(name = "pack_id", nullable = false, length = 64)
    private String packId;

    @Column(name = "pack_version", nullable = false)
    private int packVersion;

    @Column(name = "party_id", nullable = false, length = 64)
    private String partyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PackStatus status;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "checklist_version", nullable = false, length = 32)
    private String checklistVersion;

    @Column(name = "prompt_version", length = 32)
    private String promptVersion;

    @Column(name = "model_id", length = 128)
    private String modelId;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(name = "tool_outputs_hash_root", nullable = false, length = 64)
    private String toolOutputsHashRoot;

    // getters/setters deliberately omitted in skeleton;
    // entity is write-once then treated as @Immutable by convention.

    protected CaPackEntity() { /* JPA */ }
}
