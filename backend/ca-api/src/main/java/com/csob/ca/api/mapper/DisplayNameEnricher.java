package com.csob.ca.api.mapper;

import com.csob.ca.shared.dto.CaPackDto;

/**
 * Adds display names for username-valued fields on API responses.
 * Usernames remain immutable in the persisted DTO; display names are
 * resolved at read-time (per CLAUDE.md: UserNameResolverService pattern,
 * HashMap lookup, no DB call).
 *
 * Applied only at the controller boundary, never inside ca-orchestration.
 */
public interface DisplayNameEnricher {
    CaPackDto enrich(CaPackDto pack);
}
