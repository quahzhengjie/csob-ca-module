package com.csob.ca.persistence.entity;

import com.csob.ca.shared.enums.PackStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Canonical per-pack aggregate row. Write-once by {@code JpaPackRepository};
 * treated as immutable thereafter by application code. The DB schema does
 * not enforce immutability — operator tooling retains the ability to correct
 * state under authority.
 *
 * Nested DTOs serialise into the {@code *_json} CLOB columns via Jackson
 * at the repository boundary. No tool/finding/reviewer tables today
 * (deferred to a later normalisation pass).
 */
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

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;

    @Column(name = "model_id", length = 128)
    private String modelId;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(name = "tool_outputs_hash_root", nullable = false, length = 64)
    private String toolOutputsHashRoot;

    @Lob @Column(name = "party_facts_json",       nullable = false) private String partyFactsJson;
    @Lob @Column(name = "checklist_result_json",  nullable = false) private String checklistResultJson;
    @Lob @Column(name = "tool_outputs_json",      nullable = false) private String toolOutputsJson;
    @Lob @Column(name = "raw_ai_output_json")                        private String rawAiOutputJson;
    @Lob @Column(name = "validation_report_json")                    private String validationReportJson;
    @Lob @Column(name = "final_summary_json")                        private String finalSummaryJson;
    @Lob @Column(name = "reviewer_actions_json",  nullable = false) private String reviewerActionsJson;
    @Lob @Column(name = "sign_off_chain_json",    nullable = false) private String signOffChainJson;

    public CaPackEntity() { /* JPA + repository construction */ }

    // ---- getters ----

    public String     getPackId()              { return packId; }
    public int        getPackVersion()         { return packVersion; }
    public String     getPartyId()             { return partyId; }
    public PackStatus getStatus()              { return status; }
    public String     getCreatedBy()           { return createdBy; }
    public Instant    getCreatedAt()           { return createdAt; }
    public String     getChecklistVersion()    { return checklistVersion; }
    public String     getPromptVersion()       { return promptVersion; }
    public String     getModelId()             { return modelId; }
    public String     getModelVersion()        { return modelVersion; }
    public String     getToolOutputsHashRoot() { return toolOutputsHashRoot; }
    public String     getPartyFactsJson()      { return partyFactsJson; }
    public String     getChecklistResultJson() { return checklistResultJson; }
    public String     getToolOutputsJson()     { return toolOutputsJson; }
    public String     getRawAiOutputJson()     { return rawAiOutputJson; }
    public String     getValidationReportJson(){ return validationReportJson; }
    public String     getFinalSummaryJson()    { return finalSummaryJson; }
    public String     getReviewerActionsJson() { return reviewerActionsJson; }
    public String     getSignOffChainJson()    { return signOffChainJson; }

    // ---- setters ----
    // Kept package-private-ish via public for JpaPackRepository;
    // callers MUST NOT invoke these after the row has been persisted.

    public void setPackId(String v)              { this.packId = v; }
    public void setPackVersion(int v)            { this.packVersion = v; }
    public void setPartyId(String v)             { this.partyId = v; }
    public void setStatus(PackStatus v)          { this.status = v; }
    public void setCreatedBy(String v)           { this.createdBy = v; }
    public void setCreatedAt(Instant v)          { this.createdAt = v; }
    public void setChecklistVersion(String v)    { this.checklistVersion = v; }
    public void setPromptVersion(String v)       { this.promptVersion = v; }
    public void setModelId(String v)             { this.modelId = v; }
    public void setModelVersion(String v)        { this.modelVersion = v; }
    public void setToolOutputsHashRoot(String v) { this.toolOutputsHashRoot = v; }
    public void setPartyFactsJson(String v)      { this.partyFactsJson = v; }
    public void setChecklistResultJson(String v) { this.checklistResultJson = v; }
    public void setToolOutputsJson(String v)     { this.toolOutputsJson = v; }
    public void setRawAiOutputJson(String v)     { this.rawAiOutputJson = v; }
    public void setValidationReportJson(String v){ this.validationReportJson = v; }
    public void setFinalSummaryJson(String v)    { this.finalSummaryJson = v; }
    public void setReviewerActionsJson(String v) { this.reviewerActionsJson = v; }
    public void setSignOffChainJson(String v)    { this.signOffChainJson = v; }
}
