package com.csob.ca.persistence.audit;

import com.csob.ca.shared.dto.AuditEventDto;

import java.util.List;

/**
 * Read-only port over the persisted audit log. Separated from
 * {@link AuditWriter} so that orchestration (write side) and read
 * surfaces (controllers, replay verification, regulator exports) depend
 * on the smallest possible interface.
 *
 * The implementation in ca-persistence ({@code DbAuditReader}) returns
 * events in chronological order, exactly as they were appended. The
 * {@code AuditEventDto} returned here does NOT carry the per-row
 * {@code hash} / {@code prevHash} chain values — those are separate
 * persistence-level metadata. If/when chain verification needs to be
 * exposed externally, a dedicated {@code AuditChainProof} DTO will be
 * introduced.
 */
public interface AuditReader {

    /**
     * @param packId the pack to load events for
     * @return events in append order (oldest → newest); empty list if
     *         the packId is unknown.
     */
    List<AuditEventDto> findEventsByPackId(String packId);
}
