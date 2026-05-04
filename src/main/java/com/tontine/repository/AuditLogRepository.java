package com.tontine.repository;

import com.tontine.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    Page<AuditLog> findByAction(String action, Pageable pageable);

    Page<AuditLog> findByStatut(String statut, Pageable pageable);

    Page<AuditLog> findByCreatedAtBetween(LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    /** Filtre combiné action + statut + plage de dates (tous optionnels sauf plage). */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:statut IS NULL OR a.statut = :statut)
              AND (:userId IS NULL OR a.userId = :userId)
              AND a.createdAt BETWEEN :debut AND :fin
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> rechercher(
            @Param("action") String action,
            @Param("statut") String statut,
            @Param("userId") Long userId,
            @Param("debut") LocalDateTime debut,
            @Param("fin") LocalDateTime fin,
            Pageable pageable);

    long countByUserIdAndActionAndStatutAndCreatedAtAfter(
            Long userId, String action, String statut, LocalDateTime depuis);
}
