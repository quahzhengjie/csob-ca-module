package com.csob.ca.persistence.entity;

import com.csob.ca.shared.enums.ParseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Advisory artefact. Row is always written — even when the AI call returned
 * malformed JSON. Never joined to authoritative tables as a parent.
 */
@Entity
@Table(name = "csob_ca_raw_ai_outputs")
public class RawAiOutputEntity {

    @Id
    @Column(name = "pack_id", nullable = false, length = 64)
    private String packId;

    @Column(name = "model_id", nullable = false, length = 128)
    private String modelId;

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 16)
    private ParseStatus parseStatus;

    @Lob
    @Column(name = "raw_json_text", nullable = false)
    private String rawJsonText;

    @Lob
    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    protected RawAiOutputEntity() { /* JPA */ }
}
