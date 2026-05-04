package com.tontine.repository;

import com.tontine.entity.TransactionPaiement;
import com.tontine.enums.StatutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionPaiementRepository extends JpaRepository<TransactionPaiement, Long> {
    Optional<TransactionPaiement> findByTransactionId(String transactionId);
    List<TransactionPaiement> findByCotisationId(Long cotisationId);
    List<TransactionPaiement> findByStatutAndCreatedAtBefore(StatutTransaction statut, LocalDateTime before);
    boolean existsByCotisationIdAndStatut(Long cotisationId, StatutTransaction statut);
}
