package com.csob.ca.persistence.audit;

import com.csob.ca.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, String> {

    Optional<AuditLogEntity> findTopByPackIdOrderByOccurredAtDesc(String packId);
}
