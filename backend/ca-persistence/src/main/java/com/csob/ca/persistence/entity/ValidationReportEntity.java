package com.csob.ca.persistence.entity;

import com.csob.ca.shared.enums.ValidationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "csob_ca_validation_reports")
public class ValidationReportEntity {

    @Id
    @Column(name = "pack_id", nullable = false, length = 64)
    private String packId;

    @Column(name = "validated_at", nullable = false)
    private Instant validatedAt;

    @Column(name = "ai_output_hash", nullable = false, length = 64)
    private String aiOutputHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ValidationStatus status;

    @Lob
    @Column(name = "checks_json", nullable = false)
    private String checksJson;

    protected ValidationReportEntity() { /* JPA */ }
}
