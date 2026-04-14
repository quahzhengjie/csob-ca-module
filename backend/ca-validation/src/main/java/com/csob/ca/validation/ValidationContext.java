package com.csob.ca.validation;

import com.csob.ca.shared.dto.AiSummaryPayloadDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.RawAiOutputDto;
import com.csob.ca.shared.dto.ToolOutputDto;

import java.util.List;

/**
 * Operational (not system-of-record) bag passed between checks in a single
 * pipeline run. Values mutate only within a run; the resulting
 * ValidationReportDto is still immutable.
 */
public final class ValidationContext {

    private final String pipelinePackId;
    private final String pipelineChecklistVersion;
    private final List<ToolOutputDto> toolOutputs;
    private final ChecklistResultDto checklistResult;
    private final RawAiOutputDto rawAiOutput;

    private boolean schemaPassed;
    private AiSummaryPayloadDto parsedPayload;

    public ValidationContext(String pipelinePackId,
                             String pipelineChecklistVersion,
                             List<ToolOutputDto> toolOutputs,
                             ChecklistResultDto checklistResult,
                             RawAiOutputDto rawAiOutput) {
        this.pipelinePackId = pipelinePackId;
        this.pipelineChecklistVersion = pipelineChecklistVersion;
        this.toolOutputs = List.copyOf(toolOutputs);
        this.checklistResult = checklistResult;
        this.rawAiOutput = rawAiOutput;
    }

    public String pipelinePackId() { return pipelinePackId; }
    public String pipelineChecklistVersion() { return pipelineChecklistVersion; }
    public List<ToolOutputDto> toolOutputs() { return toolOutputs; }
    public ChecklistResultDto checklistResult() { return checklistResult; }
    public RawAiOutputDto rawAiOutput() { return rawAiOutput; }

    public boolean schemaPassed() { return schemaPassed; }
    public AiSummaryPayloadDto parsedPayload() { return parsedPayload; }

    public void markSchemaPassed(AiSummaryPayloadDto parsedPayload) {
        this.schemaPassed = true;
        this.parsedPayload = parsedPayload;
    }
}
