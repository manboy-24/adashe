package com.tontine.repository;

import com.tontine.entity.Paiement;
import com.tontine.enums.PaiementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaiementRepository extends JpaRepository<Paiement, Long> {
    Optional<Paiement> findByReferenceTransaction(String referenceTransaction);
    Optional<Paiement> findByGatewayTransactionId(String gatewayTransactionId);

    /** Verrou pessimiste — utilisé dans le webhook pour éviter le double traitement. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Paiement p WHERE p.referenceTransaction = :ref")
    Optional<Paiement> findByReferenceTransactionForUpdate(@Param("ref") String ref);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Paiement p WHERE p.gatewayTransactionId = :gwId")
    Optional<Paiement> findByGatewayTransactionIdForUpdate(@Param("gwId") String gwId);
    List<Paiement> findByMembreIdOrderByCreatedAtDesc(Long membreId);

    /** Vérifie qu'un paiement EN_ATTENTE n'existe pas déjà pour ce membre (anti-doublon). */
    boolean existsByMembreIdAndStatut(Long membreId, PaiementStatus statut);

    /**
     * Paiements EN_ATTENTE créés avant une date limite (pour expiration automatique).
     * JOIN FETCH membre/utilisateur/tontine : annulerSiEnAttente (clearAutomatically)
     * détache les entités — les relations doivent être initialisées avant l'UPDATE.
     */
    @Query("SELECT p FROM Paiement p JOIN FETCH p.membre m JOIN FETCH m.utilisateur JOIN FETCH m.tontine " +
           "WHERE p.statut = :statut AND p.createdAt < :limit")
    List<Paiement> findByStatutAndCreatedAtBefore(@Param("statut") PaiementStatus statut, @Param("limit") LocalDateTime limit);

    /**
     * UPDATE atomique : n'annule que si le statut est encore EN_ATTENTE au moment de l'UPDATE.
     * Évite d'écraser un PAYE posé par le webhook entre le SELECT et le save du scheduler.
     * Retourne le nombre de lignes réellement modifiées.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Paiement p SET p.statut = com.tontine.enums.PaiementStatus.ANNULE, p.messageOperateur = 'Expiré — annulé automatiquement' WHERE p.id = :id AND p.statut = com.tontine.enums.PaiementStatus.EN_ATTENTE")
    int annulerSiEnAttente(@Param("id") Long id);

    /** Annule les paiements EN_ATTENTE expirés d'un membre (>30 min) avant d'en créer un nouveau. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Paiement p SET p.statut = com.tontine.enums.PaiementStatus.ANNULE, p.messageOperateur = 'Expiré — annulé automatiquement' WHERE p.membre.id = :membreId AND p.statut = :statut AND p.createdAt < :limit")
    void annulerPaiementsExpiresParMembre(@Param("membreId") Long membreId, @Param("statut") PaiementStatus statut, @Param("limit") LocalDateTime limit);

    /** Tous les paiements d'un utilisateur, toutes tontines confondues. */
    @Query("SELECT p FROM Paiement p JOIN FETCH p.membre m JOIN FETCH m.utilisateur u WHERE u.id = :userId ORDER BY p.createdAt DESC")
    List<Paiement> findByUtilisateurIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}
