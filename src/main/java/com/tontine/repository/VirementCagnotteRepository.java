package com.tontine.repository;

import com.tontine.entity.VirementCagnotte;
import com.tontine.enums.VirementAmendeStatut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VirementCagnotteRepository extends JpaRepository<VirementCagnotte, Long> {
    Optional<VirementCagnotte> findByTirageId(Long tirageId);
}
