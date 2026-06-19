package com.tontine.scheduler;

import com.tontine.entity.Tirage;
import com.tontine.enums.NotificationType;
import com.tontine.enums.TirageAcceptationStatut;
import com.tontine.repository.TirageRepository;
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
 * Auto-accepte les tirages dont le délai de réponse du gagnant (15 min, voir
 * TontineServiceImpl.FENETRE_REPONSE_MINUTES) est écoulé sans réponse —
 * silence = acceptation implicite, décidé volontairement pour ne pas bloquer
 * le cycle si le gagnant est simplement injoignable un moment.
 *
 * Protégé par ShedLock : ne s'exécute qu'une seule fois même si plusieurs
 * instances du serveur tournent en parallèle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TirageAcceptationScheduler {

    private final TirageRepository    tirageRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 * * * * ?")
    @SchedulerLock(name = "tirageAcceptation", lockAtMostFor = "2m", lockAtLeastFor = "10s")
    @Transactional
    public void autoAccepterTiragesExpires() {
        List<Tirage> expires = tirageRepository.findByStatutAcceptationAndDateExpirationReponseBeforeAndEnLitigeFalse(
                TirageAcceptationStatut.EN_ATTENTE, LocalDateTime.now());

        if (expires.isEmpty()) return;

        for (Tirage tirage : expires) {
            tirage.setStatutAcceptation(TirageAcceptationStatut.ACCEPTE);
            tirageRepository.save(tirage);

            notificationService.creerNotification(
                    tirage.getTontine().getCreateur(), tirage.getTontine(),
                    "⏱️ Délai de réponse écoulé",
                    tirage.getBeneficiaire().getUtilisateur().getPrenom() + " "
                            + tirage.getBeneficiaire().getUtilisateur().getNom()
                            + " n'a pas répondu dans les 15 minutes — la cagnotte est considérée "
                            + "acceptée. Vous pouvez confirmer le tirage.",
                    NotificationType.TIRAGE_EFFECTUE);

            log.info("[TirageAcceptation] Tirage {} auto-accepté (silence) pour tontine {}",
                    tirage.getId(), tirage.getTontine().getId());
        }

        log.info("[TirageAcceptation] {} tirage(s) auto-accepté(s)", expires.size());
    }
}
