package com.csob.ca.tools.adapter;

import com.csob.ca.shared.dto.tool.ScreeningToolPayload;

public interface ScreeningTool {
    ScreeningToolPayload fetchScreeningResults(String partyId);
}
