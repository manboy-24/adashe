package com.tontine.repository;

import com.tontine.entity.Don;
import com.tontine.enums.PaiementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DonRepository extends JpaRepository<Don, Long> {

    Optional<Don> findByReferenceTransaction(String ref);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Don d WHERE d.referenceTransaction = :ref")
    Optional<Don> findByReferenceTransactionForUpdate(@Param("ref") String ref);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Don d WHERE d.gatewayTransactionId = :txId")
    Optional<Don> findByGatewayTransactionIdForUpdate(@Param("txId") String txId);

    List<Don> findByUtilisateurIdOrderByCreatedAtDesc(Long utilisateurId);

    boolean existsByUtilisateurIdAndStatut(Long utilisateurId, PaiementStatus statut);
}
