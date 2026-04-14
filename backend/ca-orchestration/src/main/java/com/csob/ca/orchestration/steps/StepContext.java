package com.csob.ca.orchestration.steps;

import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.PartyFactsDto;
import com.csob.ca.shared.dto.RawAiOutputDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.ValidationReportDto;
import com.csob.ca.shared.enums.PackStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Operational accumulator carried across steps within a single pack run.
 * Intentionally mutable (within one run) so each step can record its output.
 * The final CaPackDto built from this context is deep-immutable.
 *
 * This class is NOT shared across pipeline runs and is NOT persisted.
 */
public final class StepContext {

    private final String packId;
    private final String partyId;
    private final String checklistVersion;
    private final String promptVersion;

    private PackStatus status = PackStatus.INITIATED;
    private final List<ToolOutputDto> toolOutputs = new ArrayList<>();
    private PartyFactsDto partyFacts;
    private ChecklistResultDto checklistResult;
    private RawAiOutputDto rawAiOutput;
    private ValidationReportDto validationReport;

    public StepContext(String packId,
                       String partyId,
                       String checklistVersion,
                       String promptVersion) {
        this.packId = packId;
        this.partyId = partyId;
        this.checklistVersion = checklistVersion;
        this.promptVersion = promptVersion;
    }

    public String packId() { return packId; }
    public String partyId() { return partyId; }
    public String checklistVersion() { return checklistVersion; }
    public String promptVersion() { return promptVersion; }
    public PackStatus status() { return status; }
    public List<ToolOutputDto> toolOutputs() { return Collections.unmodifiableList(toolOutputs); }
    public PartyFactsDto partyFacts() { return partyFacts; }
    public ChecklistResultDto checklistResult() { return checklistResult; }
    public RawAiOutputDto rawAiOutput() { return rawAiOutput; }
    public ValidationReportDto validationReport() { return validationReport; }

    public void advanceStatus(PackStatus next) {
        com.csob.ca.orchestration.PackLifecycle.requireAllowed(status, next);
        this.status = next;
    }

    public void addToolOutput(ToolOutputDto output) { this.toolOutputs.add(output); }
    public void setPartyFacts(PartyFactsDto facts) { this.partyFacts = facts; }
    public void setChecklistResult(ChecklistResultDto r) { this.checklistResult = r; }
    public void setRawAiOutput(RawAiOutputDto r) { this.rawAiOutput = r; }
    public void setValidationReport(ValidationReportDto r) { this.validationReport = r; }
}
