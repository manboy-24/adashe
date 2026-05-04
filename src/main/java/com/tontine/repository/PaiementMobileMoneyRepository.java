package com.tontine.repository;
import com.tontine.entity.PaiementMobileMoney;
import com.tontine.enums.PaiementMobileMoneyStatut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public interface PaiementMobileMoneyRepository extends JpaRepository<PaiementMobileMoney, Long> {
    Optional<PaiementMobileMoney> findByReferenceInterne(String referenceInterne);
    Optional<PaiementMobileMoney> findByReferenceOperateur(String referenceOperateur);
    List<PaiementMobileMoney> findByUtilisateurIdOrderByCreatedAtDesc(Long userId);
    List<PaiementMobileMoney> findByStatutAndDateExpirationBefore(PaiementMobileMoneyStatut statut, LocalDateTime date);
    List<PaiementMobileMoney> findByStatutAndNombreVerificationsLessThan(PaiementMobileMoneyStatut statut, int max);
}
