package com.tontine.repository;

import com.tontine.entity.VirementAmende;
import com.tontine.enums.VirementAmendeStatut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VirementAmendeRepository extends JpaRepository<VirementAmende, Long> {

    Page<VirementAmende> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<VirementAmende> findByStatutOrderByCreatedAtDesc(VirementAmendeStatut statut, Pageable pageable);

    List<VirementAmende> findByStatut(VirementAmendeStatut statut);
}
