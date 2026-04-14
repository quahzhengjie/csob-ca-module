package com.csob.ca.validation.support;

import com.csob.ca.shared.dto.CitationDto;
import com.csob.ca.shared.dto.ChecklistResultDto;
import com.csob.ca.shared.dto.ToolOutputDto;

import java.util.List;
import java.util.Optional;

/**
 * Resolves a CitationDto against a pack's frozen evidence set:
 * the list of ToolOutputs + the ChecklistResult. Returns the observed
 * value at the cited fieldPath, or empty if the citation does not resolve.
 *
 * Used by CitationResolvabilityChecker and FactGroundingChecker.
 */
public interface CitationResolver {
    Optional<String> resolve(CitationDto citation,
                             List<ToolOutputDto> toolOutputs,
                             ChecklistResultDto checklistResult);
}
