package com.tontine.repository;

import com.tontine.entity.Cotisation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CotisationRepository extends JpaRepository<Cotisation, Long> {

    Page<Cotisation> findByTontineIdOrderByCreatedAtDesc(Long tontineId, Pageable pageable);

    @Query("SELECT c FROM Cotisation c JOIN FETCH c.membre m JOIN FETCH m.utilisateur u WHERE c.tontine.id = :tontineId ORDER BY c.numeroCycle ASC, m.ordreTour ASC NULLS LAST")
    List<Cotisation> findAllByTontineIdForExport(@Param("tontineId") Long tontineId);

    Optional<Cotisation> findByMembreIdAndTontineIdAndNumeroCycle(
            Long membreId, Long tontineId, Integer numeroCycle);

    @Query("SELECT COALESCE(SUM(c.montant), 0) FROM Cotisation c WHERE c.tontine.id = :tontineId AND c.statut = 'PAYE'")
    BigDecimal sumMontantPayeByTontineId(@Param("tontineId") Long tontineId);

    @Query("SELECT COALESCE(SUM(c.montant), 0) FROM Cotisation c WHERE c.membre.id = :membreId AND c.statut = 'PAYE'")
    BigDecimal sumMontantPayeByMembreId(@Param("membreId") Long membreId);

    @Query("SELECT COALESCE(SUM(c.montant), 0) FROM Cotisation c WHERE c.membre.utilisateur.id = :userId AND c.statut = 'PAYE'")
    BigDecimal sumMontantPayeByUtilisateurId(@Param("userId") Long userId);

    // ── Batch queries (anti N+1) ──────────────────────────────────────────────

    @Query("SELECT c.tontine.id, COALESCE(SUM(c.montant), 0) FROM Cotisation c WHERE c.tontine.id IN :ids AND c.statut = 'PAYE' GROUP BY c.tontine.id")
    List<Object[]> sumMontantPayeGroupByTontineIds(@Param("ids") List<Long> ids);

    @Query("SELECT c.membre.id, COALESCE(SUM(c.montant), 0) FROM Cotisation c WHERE c.membre.id IN :ids AND c.statut = 'PAYE' GROUP BY c.membre.id")
    List<Object[]> sumMontantPayeGroupByMembreIds(@Param("ids") List<Long> ids);

    @Query("SELECT c.membre.id FROM Cotisation c WHERE c.tontine.id = :tontineId AND c.numeroCycle = :cycle AND c.statut = 'PAYE'")
    Set<Long> findMembreIdsAyantPayePourCycle(@Param("tontineId") Long tontineId, @Param("cycle") Integer cycle);

    /** Batch : membres ayant payé pour le cycle ACTUEL de chaque tontine — 1 query au lieu de N. */
    @Query("SELECT c.tontine.id, c.membre.id FROM Cotisation c WHERE c.tontine.id IN :tontineIds AND c.statut = 'PAYE' AND c.numeroCycle = c.tontine.cycleActuel")
    List<Object[]> findMembreIdsAyantPayePourCyclesActuels(@Param("tontineIds") List<Long> tontineIds);

    @Query("SELECT COUNT(c) FROM Cotisation c WHERE c.membre.id = :membreId AND c.tontine.id = :tontineId AND c.statut = 'PAYE'")
    int countPayesByMembreIdAndTontineId(@Param("membreId") Long membreId, @Param("tontineId") Long tontineId);

    /** Cycles antérieurs ayant au moins un PAYE (cycles trackés dans le système). */
    @Query("SELECT DISTINCT c.numeroCycle FROM Cotisation c WHERE c.tontine.id = :tontineId AND c.statut = 'PAYE' AND c.numeroCycle < :cycleActuel")
    Set<Integer> findCyclesTrackesAvant(@Param("tontineId") Long tontineId, @Param("cycleActuel") Integer cycleActuel);

    /** Numéros de cycles payés par ce membre pour les cycles antérieurs. */
    @Query("SELECT c.numeroCycle FROM Cotisation c WHERE c.membre.id = :membreId AND c.tontine.id = :tontineId AND c.statut = 'PAYE' AND c.numeroCycle < :cycleActuel")
    Set<Integer> findCyclesPayesParMembre(@Param("membreId") Long membreId, @Param("tontineId") Long tontineId, @Param("cycleActuel") Integer cycleActuel);

    // ── Adashe Score : agrégats par utilisateur (toutes tontines confondues) ──

    @Query("SELECT COUNT(c) FROM Cotisation c WHERE c.membre.utilisateur.id = :userId AND c.statut = 'PAYE'")
    int countPayeesByUtilisateurId(@Param("userId") Long userId);

    /** Briefing hebdo : total collecté sur une tontine depuis une date. */
    @Query("SELECT COALESCE(SUM(c.montant), 0) FROM Cotisation c WHERE c.tontine.id = :tontineId AND c.statut = 'PAYE' AND c.datePaiement >= :depuis")
    BigDecimal sumMontantPayeDepuis(@Param("tontineId") Long tontineId, @Param("depuis") java.time.LocalDate depuis);

    @Query("SELECT COALESCE(SUM(c.montantAmende), 0) FROM Cotisation c WHERE c.membre.utilisateur.id = :userId")
    BigDecimal sumAmendesByUtilisateurId(@Param("userId") Long userId);
}
