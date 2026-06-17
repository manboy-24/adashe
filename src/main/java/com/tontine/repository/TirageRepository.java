package com.tontine.repository;

import com.tontine.entity.Tirage;
import com.tontine.enums.TirageAcceptationStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TirageRepository extends JpaRepository<Tirage, Long> {
    boolean existsByTontineIdAndNumeroCycle(Long tontineId, Integer numeroCycle);
    boolean existsByTontineIdAndConfirmeFalse(Long tontineId);
    Optional<Tirage> findByIdAndTontineId(Long id, Long tontineId);
    List<Tirage> findByTontineIdOrderByNumeroCycleAsc(Long tontineId);

    /**
     * Scheduler d'auto-acceptation : tirages dont le délai de réponse est écoulé
     * sans réponse — exclut les tirages en litige (enLitige=true), qu'un
     * signalement gèle jusqu'à résolution par l'admin.
     */
    List<Tirage> findByStatutAcceptationAndDateExpirationReponseBeforeAndEnLitigeFalse(
            TirageAcceptationStatut statutAcceptation, LocalDateTime maintenant);

    @Query("SELECT COALESCE(SUM(t.montantDistribue), 0) FROM Tirage t WHERE t.tontine.id = :tontineId")
    BigDecimal sumMontantDistribueByTontineId(@Param("tontineId") Long tontineId);

    /** Scheduler retards : tirages confirmés effectués à la date donnée. */
    @Query("SELECT t FROM Tirage t WHERE t.dateTirage = :date AND t.confirme = true")
    List<Tirage> findByDateTirage(@Param("date") LocalDate date);

    /** Batch : total distribué par tontine (évite N queries dans getMesTontines). */
    @Query("SELECT t.tontine.id, COALESCE(SUM(t.montantDistribue), 0) FROM Tirage t WHERE t.tontine.id IN :ids GROUP BY t.tontine.id")
    List<Object[]> sumMontantDistribueGroupByTontineIds(@Param("ids") List<Long> ids);
}
