package com.csob.ca.tools.support;

import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.dto.tool.ToolPayload;
import com.csob.ca.shared.enums.ToolId;

import java.time.Instant;

/**
 * Builds immutable ToolOutputDto rows with canonical payload hashing.
 * Applied uniformly to every tool result before persisting it as part of
 * the frozen snapshot the checklist engine reads.
 */
public interface ToolOutputFactory {
    ToolOutputDto build(String packId,
                        ToolId toolId,
                        Instant requestedAt,
                        Instant fetchedAt,
                        String sourceVersion,
                        ToolPayload payload);
}
