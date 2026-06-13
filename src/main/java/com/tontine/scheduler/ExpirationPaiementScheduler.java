package com.tontine.scheduler;

import com.tontine.entity.Paiement;
import com.tontine.enums.NotificationType;
import com.tontine.enums.PaiementStatus;
import com.tontine.repository.PaiementRepository;
import com.tontine.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Expire les paiements Monetbil restés EN_ATTENTE plus de 30 minutes.
 * Sans cette purge, un membre dont le callback n'arrive jamais ne peut plus payer
 * (bloqué par l'anti-concurrent EN_ATTENTE dans initierPaiement).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpirationPaiementScheduler {

    private static final int DELAI_EXPIRATION_MINUTES = 30;

    private final PaiementRepository  paiementRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 */15 * * * ?")
    @SchedulerLock(name = "expirationPaiements", lockAtMostFor = "5m", lockAtLeastFor = "30s")
    @Transactional
    public void expirer() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(DELAI_EXPIRATION_MINUTES);
        List<Paiement> expires = paiementRepository
                .findByStatutAndCreatedAtBefore(PaiementStatus.EN_ATTENTE, limite);

        if (expires.isEmpty()) return;

        for (Paiement p : expires) {
            p.setStatut(PaiementStatus.ANNULE);
            paiementRepository.save(p);

            notificationService.creerNotification(
                    p.getMembre().getUtilisateur(),
                    p.getMembre().getTontine(),
                    "Paiement expiré",
                    "Votre paiement Mobile Money (réf. " + p.getReferenceTransaction()
                            + ") n'a pas été confirmé dans les 30 minutes et a été annulé. "
                            + "Vous pouvez réessayer.",
                    NotificationType.RETARD_PAIEMENT);

            log.info("[ExpirationPaiement] Paiement {} expiré pour membre {}",
                    p.getReferenceTransaction(), p.getMembre().getId());
        }

        log.info("[ExpirationPaiement] {} paiement(s) EN_ATTENTE expirés", expires.size());
    }
}
