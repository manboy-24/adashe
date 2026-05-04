package com.tontine.scheduler;

import com.tontine.entity.Tontine;
import com.tontine.enums.NotificationType;
import com.tontine.enums.TirageType;
import com.tontine.repository.TirageRepository;
import com.tontine.repository.TontineRepository;
import com.tontine.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Déclenche automatiquement les tirages RANDOM et ROTATIF à 20h chaque jour.
 * Les tirages MANUEL envoient une notification au créateur pour qu'il agisse.
 *
 * Protégé par ShedLock : ne s'exécute qu'une seule fois même si plusieurs
 * instances du serveur tournent en parallèle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoTirageScheduler {

    private final TontineRepository    tontineRepository;
    private final TirageRepository     tirageRepository;
    private final NotificationService  notificationService;

    @Scheduled(cron = "0 0 20 * * ?")
    @SchedulerLock(name = "autoTirage", lockAtMostFor = "30m", lockAtLeastFor = "2m")
    @Transactional
    public void executerTiragesAutomatiques() {
        LocalDate today = LocalDate.now();
        List<Tontine> tontines = tontineRepository.findActivesParProchainCycle(today);

        if (tontines.isEmpty()) {
            log.debug("[AutoTirage] Aucune tontine active avec tirage prévu aujourd'hui");
            return;
        }

        log.info("[AutoTirage] {} tontine(s) avec tirage prévu le {}", tontines.size(), today);

        for (Tontine tontine : tontines) {

            // Déjà tiré pour ce cycle → skip
            if (tirageRepository.existsByTontineIdAndNumeroCycle(
                    tontine.getId(), tontine.getCycleActuel())) {
                log.debug("[AutoTirage] Tontine {} — tirage déjà effectué pour cycle {}",
                        tontine.getId(), tontine.getCycleActuel());
                continue;
            }

            // Toujours laisser le créateur lancer le tirage lui-même
            String detail = switch (tontine.getTypeTirage()) {
                case RANDOM  -> "Le bénéficiaire sera tiré au sort.";
                case ROTATIF -> "Le tirage suit l'ordre des membres.";
                case MANUEL  -> "Sélectionnez manuellement le bénéficiaire.";
            };
            notificationService.creerNotification(
                    tontine.getCreateur(), tontine,
                    "C'est le jour du tirage !",
                    "Ouvrez l'app pour lancer le tirage de « " + tontine.getNom()
                            + " ». " + detail,
                    NotificationType.RAPPEL_COTISATION);
            log.info("[AutoTirage] Tontine {} ({}) — notification créateur envoyée",
                    tontine.getId(), tontine.getTypeTirage());
        }
    }
}
