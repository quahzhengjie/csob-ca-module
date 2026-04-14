package com.csob.ca.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only hash-chained audit log. One row per pipeline transition
 * and reviewer action.
 */
@Entity
@Table(name = "csob_ca_audit_log")
public class AuditLogEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "pack_id", nullable = false, length = 64)
    private String packId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Lob
    @Column(name = "event_json", nullable = false)
    private String eventJson;

    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    @Column(name = "hash", nullable = false, length = 64)
    private String hash;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditLogEntity() { /* JPA */ }
}
