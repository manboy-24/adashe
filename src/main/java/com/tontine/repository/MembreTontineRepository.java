package com.tontine.repository;

import com.tontine.entity.MembreTontine;
import com.tontine.enums.MembreStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MembreTontineRepository extends JpaRepository<MembreTontine, Long> {

    List<MembreTontine> findByTontineId(Long tontineId);

    List<MembreTontine> findByTontineIdAndActifTrue(Long tontineId);

    /** Retourne les membres actifs ET en attente (exclut les retirés). */
    List<MembreTontine> findByTontineIdAndStatutMembreNot(Long tontineId, MembreStatut statut);

    List<MembreTontine> findByUtilisateurId(Long utilisateurId);

    Optional<MembreTontine> findByUtilisateurIdAndTontineId(Long utilisateurId, Long tontineId);

    boolean existsByUtilisateurIdAndTontineId(Long utilisateurId, Long tontineId);

    @Query("SELECT m FROM MembreTontine m WHERE m.tontine.id = :tontineId AND m.aCagnotteSurCycleActuel = false AND m.actif = true")
    List<MembreTontine> findEligiblesPourTirage(@Param("tontineId") Long tontineId);

    // ── Batch queries (anti N+1) ──────────────────────────────────────────────

    @Query("SELECT m FROM MembreTontine m JOIN FETCH m.utilisateur WHERE m.tontine.id IN :tontineIds AND m.statutMembre <> :statut")
    List<MembreTontine> findByTontineIdInAndStatutMembreNot(@Param("tontineIds") List<Long> tontineIds, @Param("statut") MembreStatut statut);

    @Query("SELECT m.tontine.id, COUNT(m) FROM MembreTontine m WHERE m.tontine.id IN :ids AND m.actif = true GROUP BY m.tontine.id")
    List<Object[]> countActifGroupByTontineIds(@Param("ids") List<Long> ids);

    /** Invitations EN_ATTENTE non acceptées dans le délai imparti. */
    @Query("SELECT m FROM MembreTontine m WHERE m.statutMembre = 'EN_ATTENTE' AND m.dateAdhesion < :limite")
    List<MembreTontine> findInvitationsExpirees(@Param("limite") java.time.LocalDateTime limite);
}