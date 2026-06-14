package com.tontine.repository;

import com.tontine.entity.Tontine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TontineRepository extends JpaRepository<Tontine, Long> {

    @Query(value      = "SELECT DISTINCT t FROM Tontine t JOIN t.membres m " +
                        "WHERE m.utilisateur.id = :userId " +
                        "AND m.statutMembre <> com.tontine.enums.MembreStatut.RETIRE",
           countQuery = "SELECT COUNT(DISTINCT t) FROM Tontine t JOIN t.membres m " +
                        "WHERE m.utilisateur.id = :userId " +
                        "AND m.statutMembre <> com.tontine.enums.MembreStatut.RETIRE")
    Page<Tontine> findAllByMembreId(@Param("userId") Long userId, Pageable pageable);

    Optional<Tontine> findByCodeInvitation(String codeInvitation);

    @Query("SELECT t FROM Tontine t WHERE t.createur.id = :userId")
    List<Tontine> findByCreateurId(@Param("userId") Long userId);

    /** Scheduler : tontines actives dont le prochain cycle tombe à la date donnée. */
    @Query("SELECT t FROM Tontine t WHERE t.statut = com.tontine.enums.TontineStatus.ACTIVE AND t.dateProchainCycle = :date")
    List<Tontine> findActivesParProchainCycle(@Param("date") LocalDate date);

    /** Scheduler : toutes les tontines actives avec un prochain cycle défini. */
    @Query("SELECT t FROM Tontine t WHERE t.statut = com.tontine.enums.TontineStatus.ACTIVE AND t.dateProchainCycle IS NOT NULL")
    List<Tontine> findActivesAvecCycleDefini();

    /** Scheduler rappel hebdo : toutes les tontines actives. */
    @Query("SELECT t FROM Tontine t WHERE t.statut = com.tontine.enums.TontineStatus.ACTIVE")
    List<Tontine> findToutesActives();
}
