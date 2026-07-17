package com.tontine.repository;

import com.tontine.entity.ScoreFiabilite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScoreFiabiliteRepository extends JpaRepository<ScoreFiabilite, Long> {

    Optional<ScoreFiabilite> findByUtilisateurId(Long utilisateurId);
}
