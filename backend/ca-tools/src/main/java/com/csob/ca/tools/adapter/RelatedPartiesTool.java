package com.csob.ca.tools.adapter;

import com.csob.ca.shared.dto.tool.RelatedPartiesToolPayload;

public interface RelatedPartiesTool {
    RelatedPartiesToolPayload fetchRelatedParties(String partyId);
}
