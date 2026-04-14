package com.csob.ca.persistence.entity;

import com.csob.ca.shared.enums.ToolId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "csob_ca_tool_outputs")
public class ToolOutputEntity {

    @Id
    @Column(name = "tool_output_id", nullable = false, length = 64)
    private String toolOutputId;

    @Column(name = "pack_id", nullable = false, length = 64)
    private String packId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_id", nullable = false, length = 32)
    private ToolId toolId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "source_version", nullable = false, length = 64)
    private String sourceVersion;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    protected ToolOutputEntity() { /* JPA */ }
}
