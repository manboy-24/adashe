package com.tontine.scheduler;

import com.tontine.entity.MembreTontine;
import com.tontine.entity.Tontine;
import com.tontine.enums.NotificationType;
import com.tontine.enums.PaiementStatus;
import com.tontine.repository.CotisationRepository;
import com.tontine.repository.MembreTontineRepository;
import com.tontine.repository.TontineRepository;
import com.tontine.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Résumé hebdomadaire envoyé chaque lundi à 8h à tous les membres actifs.
 * Correspond au toggle "Rappels hebdo" dans les préférences Android.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RappelHebdoScheduler {

    private final TontineRepository       tontineRepository;
    private final MembreTontineRepository membreRepository;
    private final CotisationRepository    cotisationRepository;
    private final NotificationService     notificationService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Scheduled(cron = "0 0 8 ? * MON")
    @SchedulerLock(name = "rappelHebdo", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    @Transactional(readOnly = true)
    public void envoyerResumesHebdo() {
        List<Tontine> tontines = tontineRepository.findToutesActives();
        int total = 0;

        for (Tontine tontine : tontines) {
            List<MembreTontine> membres = membreRepository.findByTontineIdAndActifTrue(tontine.getId());

            long nbAyantPaye = membres.stream()
                    .filter(m -> aPayeCycle(m, tontine))
                    .count();

            String prochainCycle = tontine.getDateProchainCycle() != null
                    ? tontine.getDateProchainCycle().format(FMT)
                    : "à définir";

            for (MembreTontine membre : membres) {
                boolean aPaye = aPayeCycle(membre, tontine);
                String statutPaiement = aPaye ? "✅ Vous avez cotisé ce cycle." : "⏳ Cotisation en attente.";

                String message = "Semaine " + tontine.getNom() + " : "
                        + nbAyantPaye + "/" + membres.size() + " membres ont cotisé. "
                        + statutPaiement
                        + " Prochain cycle : " + prochainCycle + ".";

                notificationService.creerNotification(
                        membre.getUtilisateur(),
                        tontine,
                        "📊 Résumé hebdo — " + tontine.getNom(),
                        message,
                        NotificationType.RAPPEL_COTISATION
                );
            }

            total += membres.size();
            log.info("[RappelHebdo] Résumé envoyé pour tontine={} ({} membres)", tontine.getNom(), membres.size());
        }

        log.info("[RappelHebdo] {} notifications envoyées au total", total);
    }

    private boolean aPayeCycle(MembreTontine membre, Tontine tontine) {
        return cotisationRepository
                .findByMembreIdAndTontineIdAndNumeroCycle(
                        membre.getId(), tontine.getId(), tontine.getCycleActuel())
                .filter(c -> c.getStatut() == PaiementStatus.PAYE)
                .isPresent();
    }
}
