package com.csob.ca.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Advisory — analyst-edited text. Never joined to authoritative tables
 * as a parent.
 */
@Entity
@Table(name = "csob_ca_final_summaries")
public class FinalSummaryEntity {

    @Id
    @Column(name = "pack_id", nullable = false, length = 64)
    private String packId;

    @Lob
    @Column(name = "edited_text", nullable = false)
    private String editedText;

    @Column(name = "edited_by", nullable = false, length = 128)
    private String editedBy;

    @Column(name = "edited_at", nullable = false)
    private Instant editedAt;

    @Column(name = "base_ai_output_hash", nullable = false, length = 64)
    private String baseAiOutputHash;

    protected FinalSummaryEntity() { /* JPA */ }
}
