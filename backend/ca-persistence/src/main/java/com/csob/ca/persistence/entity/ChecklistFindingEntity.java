package com.csob.ca.persistence.entity;

import com.csob.ca.shared.enums.RuleSeverity;
import com.csob.ca.shared.enums.RuleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "csob_ca_checklist_findings")
public class ChecklistFindingEntity {

    @Id
    @Column(name = "finding_id", nullable = false, length = 64)
    private String findingId;

    @Column(name = "pack_id", nullable = false, length = 64)
    private String packId;

    @Column(name = "rule_id", nullable = false, length = 64)
    private String ruleId;

    @Column(name = "description", nullable = false, length = 512)
    private String description;

    @Column(name = "regulatory_reference", nullable = false, length = 128)
    private String regulatoryReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private RuleSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RuleStatus status;

    @Lob
    @Column(name = "evidence_json", nullable = false)
    private String evidenceJson;

    protected ChecklistFindingEntity() { /* JPA */ }
}
