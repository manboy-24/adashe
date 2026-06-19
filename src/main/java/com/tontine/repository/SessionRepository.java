package com.tontine.repository;

import com.tontine.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByRefreshTokenHash(String hash);

    Optional<Session> findByPreviousRefreshTokenHash(String hash);

    List<Session> findByUtilisateurIdAndActiveTrue(Long utilisateurId);

    boolean existsByUtilisateurIdAndDeviceIdAndActiveTrue(Long utilisateurId, String deviceId);

    Optional<Session> findByUtilisateurIdAndDeviceIdAndActiveTrue(Long utilisateurId, String deviceId);

    @Modifying
    @Query("UPDATE Session s SET s.active = false WHERE s.utilisateur.id = :userId AND s.id <> :excludeId")
    void deactivateAllExcept(@Param("userId") Long userId, @Param("excludeId") Long excludeId);

    @Modifying
    @Query("UPDATE Session s SET s.active = false WHERE s.utilisateur.id = :userId")
    void deactivateAll(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Session s WHERE s.expiresAt < :now")
    void deleteExpired(@Param("now") LocalDateTime now);
}
