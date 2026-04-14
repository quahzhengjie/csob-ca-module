package com.csob.ca.persistence.repository;

import com.csob.ca.persistence.entity.CaPackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data interface — used by the PackRepository implementation, NOT
 * by ca-orchestration directly. Kept internal to ca-persistence.
 */
public interface CaPackJpaRepository extends JpaRepository<CaPackEntity, String> {
}
