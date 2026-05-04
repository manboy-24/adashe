package com.tontine.repository;

import com.tontine.entity.Paiement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
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

    /** Tous les paiements d'un utilisateur, toutes tontines confondues. */
    @Query("SELECT p FROM Paiement p JOIN FETCH p.membre m JOIN FETCH m.utilisateur u WHERE u.id = :userId ORDER BY p.createdAt DESC")
    List<Paiement> findByUtilisateurIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}
