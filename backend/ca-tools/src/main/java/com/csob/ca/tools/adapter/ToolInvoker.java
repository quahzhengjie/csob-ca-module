package com.csob.ca.tools.adapter;

import com.csob.ca.shared.dto.ToolOutputDto;
import com.csob.ca.shared.enums.ToolId;

/**
 * Fixed, typed adapters over upstream KYC sources. Invoked only by
 * ca-orchestration in a backend-controlled, linear sequence.
 * NOT exposed to the AI layer. NO autonomous selection.
 */
public interface ToolInvoker {
    ToolOutputDto invoke(String packId, ToolId toolId, String partyId);
}
