package com.csob.ca.validation;

import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.RawAiOutputDto;
import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.ValidationReportDto;

import java.util.List;

/**
 * Composes the deterministic validation checks in a fixed order and
 * produces an immutable ValidationReportDto. status = VALID iff every
 * check passes; otherwise REJECTED.
 *
 * No silent retries. No model calls.
 */
public interface ValidationPipeline {
    ValidationReportDto validate(String pipelinePackId,
                                 String pipelineChecklistVersion,
                                 List<ToolOutputDto> toolOutputs,
                                 ChecklistResultDto checklistResult,
                                 RawAiOutputDto rawAiOutput);
}
