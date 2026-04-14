package com.csob.ca.tools.adapter;

import com.csob.ca.shared.dto.tool.PartyToolPayload;

public interface PartyTool {
    PartyToolPayload fetchPartyRecord(String partyId);
}
