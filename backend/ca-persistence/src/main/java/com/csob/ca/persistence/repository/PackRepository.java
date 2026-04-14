package com.csob.ca.persistence.repository;

import com.csob.ca.shared.dto.CaPackDto;
import com.csob.ca.shared.dto.ValidationReportDto;
import com.csob.ca.shared.enums.PackStatus;

import java.util.Optional;

/**
 * Repository port consumed by ca-orchestration. Implementation lives in
 * ca-persistence.impl (future) and is wired via Spring configuration in ca-api.
 *
 * Separated from Spring Data repository interfaces so the orchestration layer
 * does not depend on Spring Data types.
 */
public interface PackRepository {

    void persistPack(CaPackDto pack);

    void appendValidation(String packId, ValidationReportDto report);

    void updateStatus(String packId, PackStatus status);

    Optional<CaPackDto> findById(String packId);

    int nextPackVersion(String partyId);
}
