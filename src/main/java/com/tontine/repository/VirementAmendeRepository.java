package com.tontine.repository;

import com.tontine.entity.VirementAmende;
import com.tontine.enums.VirementAmendeStatut;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VirementAmendeRepository extends JpaRepository<VirementAmende, Long> {

    Page<VirementAmende> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<VirementAmende> findByStatutOrderByCreatedAtDesc(VirementAmendeStatut statut, Pageable pageable);

    List<VirementAmende> findByStatut(VirementAmendeStatut statut);

    /** Verrou pessimiste pour éviter le double retry concurrent */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VirementAmende v WHERE v.id = :id")
    Optional<VirementAmende> findByIdForUpdate(@Param("id") Long id);
}
