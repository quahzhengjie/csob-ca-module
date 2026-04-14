package com.csob.ca.shared.dto.tool;

import com.csob.ca.shared.dto.RelatedPartyDto;

import java.util.List;

public record RelatedPartiesToolPayload(List<RelatedPartyDto> relatedParties) implements ToolPayload {
    public RelatedPartiesToolPayload {
        relatedParties = List.copyOf(relatedParties);
    }
}
