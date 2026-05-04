package com.tontine.repository;

import com.tontine.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByTelephone(String telephone);
    Optional<Utilisateur> findByEmail(String email);
    Optional<Utilisateur> findByRefreshToken(String refreshToken);
    Optional<Utilisateur> findByPreviousRefreshTokenHash(String previousRefreshTokenHash);
    boolean existsByTelephone(String telephone);
    boolean existsByEmail(String email);
    List<Utilisateur> findTop5ByTelephoneContainingAndActifTrue(String telephone);
    Optional<Utilisateur> findByFcmToken(String fcmToken);

    /** Incrément atomique en base — évite la race condition sur le compteur PIN. */
    @Modifying
    @Query("UPDATE Utilisateur u SET u.tentativesPinEchouees = u.tentativesPinEchouees + 1 WHERE u.id = :id")
    void incrementTentativesEchouees(@Param("id") Long id);

    /** Nettoyage nuit — efface les OTP expirés. */
    @Modifying
    @Query("UPDATE Utilisateur u SET u.otpCode = NULL, u.otpExpiration = NULL, u.otpPurpose = NULL WHERE u.otpExpiration < :maintenant")
    int purgerOtpExpires(@Param("maintenant") java.time.LocalDateTime maintenant);

    /** Nettoyage nuit — efface les refresh tokens expirés. */
    @Modifying
    @Query("UPDATE Utilisateur u SET u.refreshToken = NULL, u.refreshTokenExpiration = NULL, u.previousRefreshTokenHash = NULL WHERE u.refreshTokenExpiration < :maintenant")
    int purgerRefreshTokensExpires(@Param("maintenant") java.time.LocalDateTime maintenant);
}
