package com.tontine.repository;

import com.tontine.entity.VirementCommission;
import com.tontine.enums.VirementAmendeStatut;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VirementCommissionRepository extends JpaRepository<VirementCommission, Long> {

    List<VirementCommission> findByTirageId(Long tirageId);

    List<VirementCommission> findByStatut(VirementAmendeStatut statut);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VirementCommission v WHERE v.id = :id")
    Optional<VirementCommission> findByIdForUpdate(@Param("id") Long id);
}
