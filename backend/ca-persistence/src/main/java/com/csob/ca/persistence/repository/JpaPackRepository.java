package com.csob.ca.persistence.repository;

import com.csob.ca.shared.dto.CaPackDto;
import com.csob.ca.shared.dto.ValidationReportDto;
import com.csob.ca.shared.enums.PackStatus;

import java.util.Optional;

public final class JpaPackRepository implements PackRepository {

    private final CaPackJpaRepository caPackJpaRepository;

    public JpaPackRepository(CaPackJpaRepository caPackJpaRepository) {
        this.caPackJpaRepository = caPackJpaRepository;
    }

    @Override
    public void persistPack(CaPackDto pack) {
        throw new UnsupportedOperationException(
                "Skeleton — map DTO graph to entities (CaPackEntity + related rows), " +
                "save in a single transaction, and write an audit event.");
    }

    @Override
    public void appendValidation(String packId, ValidationReportDto report) {
        throw new UnsupportedOperationException("Skeleton");
    }

    @Override
    public void updateStatus(String packId, PackStatus status) {
        throw new UnsupportedOperationException(
                "Skeleton — enforce PackLifecycle.requireAllowed on the persisted status.");
    }

    @Override
    public Optional<CaPackDto> findById(String packId) {
        throw new UnsupportedOperationException("Skeleton");
    }

    @Override
    public int nextPackVersion(String partyId) {
        throw new UnsupportedOperationException("Skeleton");
    }
}
