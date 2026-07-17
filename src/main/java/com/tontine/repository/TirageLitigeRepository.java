package com.tontine.repository;

import com.tontine.entity.TirageLitige;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TirageLitigeRepository extends JpaRepository<TirageLitige, Long> {
    List<TirageLitige> findByTirageIdOrderByCreatedAtDesc(Long tirageId);
    Optional<TirageLitige> findByIdAndTirageId(Long id, Long tirageId);
    boolean existsByTirageIdAndStatut(Long tirageId, com.tontine.enums.LitigeStatut statut);

    /** Adashe Score : litiges portant sur des tirages dont l'utilisateur était bénéficiaire. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(l) FROM TirageLitige l WHERE l.tirage.beneficiaire.utilisateur.id = :userId")
    int countByBeneficiaireUtilisateurId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
