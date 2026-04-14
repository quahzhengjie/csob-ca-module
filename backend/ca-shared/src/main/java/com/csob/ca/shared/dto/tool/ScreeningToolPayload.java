package com.csob.ca.shared.dto.tool;

import com.csob.ca.shared.dto.ScreeningResultDto;

import java.util.List;

public record ScreeningToolPayload(List<ScreeningResultDto> results) implements ToolPayload {
    public ScreeningToolPayload {
        results = List.copyOf(results);
    }
}
