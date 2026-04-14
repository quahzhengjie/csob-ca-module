package com.csob.ca.shared.dto.tool;

import com.csob.ca.shared.dto.PartyCoreDto;

import java.util.Objects;

public record PartyToolPayload(PartyCoreDto party) implements ToolPayload {
    public PartyToolPayload {
        Objects.requireNonNull(party, "party");
    }
}
