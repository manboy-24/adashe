package com.tontine.scheduler;

import com.tontine.entity.*;
import com.tontine.enums.NotificationType;
import com.tontine.enums.PaiementStatus;
import com.tontine.repository.*;
import com.tontine.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class RappelCotisationScheduler {

    private final TontineRepository       tontineRepository;
    private final MembreTontineRepository membreRepository;
    private final CotisationRepository    cotisationRepository;
    private final TirageRepository        tirageRepository;
    private final NotificationService     notificationService;
    private final NotificationRepository  notificationRepository;
    private final com.tontine.service.ScoreFiabiliteService scoreFiabiliteService;
    private final ScoreFiabiliteRepository scoreFiabiliteRepository;

    // ── 1. Rappel J-3 : "Le tirage approche" ─────────────────────────────────
    @Scheduled(cron = "0 0 9 * * ?")
    @SchedulerLock(name = "rappelTroisJoursAvant", lockAtMostFor = "5m", lockAtLeastFor = "1m")
    @Transactional(readOnly = true)
    public void rappelTroisJoursAvant() {
        LocalDate cible = LocalDate.now().plusDays(3);
        List<Tontine> tontines = tontineRepository.findActivesParProchainCycle(cible);

        LocalDateTime debutJour = LocalDate.now().atStartOfDay();
        for (Tontine tontine : tontines) {
            membreRepository.findByTontineIdAndActifTrue(tontine.getId()).stream()
                .filter(m -> !aPayeCycle(m, tontine))
                .filter(m -> !notificationRepository.existsByUtilisateurIdAndReferenceIdAndTypeAndCreatedAtAfter(
                        m.getUtilisateur().getId(), tontine.getId(),
                        NotificationType.RAPPEL_COTISATION, debutJour))
                .forEach(m -> {
                    String msg = "Rappel : votre cotisation de " + tontine.getMontantContribution()
                            + " " + tontine.getDevise() + " est attendue dans 3 jours pour "
                            + tontine.getNom() + ".";
                    notificationService.creerNotification(m.getUtilisateur(), tontine,
                            "Cotisation dans 3 jours", msg, NotificationType.RAPPEL_COTISATION);
                });
            log.info("[Scheduler] Rappels J-3 pour tontine: {}", tontine.getNom());
        }
    }

    // ── 2. Rappel jour J : "Pensez à cotiser aujourd'hui" ────────────────────
    @Scheduled(cron = "0 0 8 * * ?")
    @SchedulerLock(name = "rappelJourJ", lockAtMostFor = "5m", lockAtLeastFor = "1m")
    @Transactional(readOnly = true)
    public void rappelJourJ() {
        List<Tontine> tontines = tontineRepository.findActivesParProchainCycle(LocalDate.now());

        LocalDateTime debutJourJ = LocalDate.now().atStartOfDay();
        for (Tontine tontine : tontines) {
            long nonPayers = membreRepository.findByTontineIdAndActifTrue(tontine.getId()).stream()
                .filter(m -> !aPayeCycle(m, tontine))
                .filter(m -> !notificationRepository.existsByUtilisateurIdAndReferenceIdAndTypeAndCreatedAtAfter(
                        m.getUtilisateur().getId(), tontine.getId(),
                        NotificationType.RAPPEL_COTISATION, debutJourJ))
                .peek(m -> {
                    String msg = "Aujourd'hui est le jour de cotisation pour " + tontine.getNom()
                            + ". Montant : " + tontine.getMontantContribution() + " " + tontine.getDevise()
                            + ". Payez maintenant via MTN MoMo ou Orange Money.";
                    notificationService.creerNotification(m.getUtilisateur(), tontine,
                            "Cotisez aujourd'hui !", msg, NotificationType.RAPPEL_COTISATION);
                }).count();

            if (nonPayers > 0)
                log.info("[Scheduler] Rappels Jour J: {} retardataires pour {}", nonPayers, tontine.getNom());
        }
    }

    // ── 3. Alerte 2/3 de cycle : "Vous risquez un retard" ────────────────────
    @Scheduled(cron = "0 0 10 * * ?")
    @SchedulerLock(name = "alerteDeuxTiersDuCycle", lockAtMostFor = "5m", lockAtLeastFor = "1m")
    @Transactional(readOnly = true)
    public void alerteDeuxTiersDuCycle() {
        LocalDate aujourd_hui = LocalDate.now();

        for (Tontine tontine : tontineRepository.findActivesAvecCycleDefini()) {
            long joursRestants = aujourd_hui.until(tontine.getDateProchainCycle(),
                    java.time.temporal.ChronoUnit.DAYS);
            int dureeTotal = switch (tontine.getFrequence()) {
                case HEBDOMADAIRE -> 7;
                case BIMENSUEL    -> 14;
                case MENSUEL      -> 30;
            };
            if (joursRestants > Math.max(1, dureeTotal / 3)) continue;

            membreRepository.findByTontineIdAndActifTrue(tontine.getId()).stream()
                .filter(m -> !aPayeCycle(m, tontine))
                .forEach(m -> {
                    String msg = "⚠️ Il vous reste " + joursRestants + " jour(s) pour cotiser dans "
                            + tontine.getNom() + " (cycle " + tontine.getCycleActuel() + "). "
                            + "Montant : " + tontine.getMontantContribution() + " " + tontine.getDevise() + ".";
                    notificationService.creerNotification(m.getUtilisateur(), tontine,
                            "Cotisation bientôt en retard", msg, NotificationType.RAPPEL_COTISATION);
                });
            log.info("[Scheduler] Alertes 2/3 cycle pour tontine: {}", tontine.getNom());
        }
    }

    // ── 4. Marquer retards définitifs — basé sur les tirages d'hier ──────────
    //
    // Corrige le bug précédent : dateProchainCycle est déjà mise à jour après le tirage,
    // on ne peut donc pas filtrer dessus. On utilise la date réelle du tirage (dateTirage).
    //
    @Scheduled(cron = "0 0 18 * * ?")
    @SchedulerLock(name = "marquerRetards", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    @Transactional
    public void marquerRetards() {
        LocalDate hier = LocalDate.now().minusDays(1);

        for (Tirage tirage : tirageRepository.findByDateTirage(hier)) {
            Tontine tontine = tirage.getTontine();
            // numeroCycle du tirage = cycle pour lequel on cherche les impayés
            int cycleVise = tirage.getNumeroCycle();

            List<MembreTontine> membres = membreRepository.findByTontineIdAndActifTrue(tontine.getId());
            Set<Long> ayantPaye = cotisationRepository.findMembreIdsAyantPayePourCycle(tontine.getId(), cycleVise);
            for (MembreTontine membre : membres) {
                boolean aPaye = ayantPaye.contains(membre.getId());

                if (!aPaye) {
                    membre.setNombreRetards(membre.getNombreRetards() + 1);

                    // Blocage automatique à partir de 2 retards
                    if (membre.getNombreRetards() >= 2
                            && membre.getStatutMembre() != com.tontine.enums.MembreStatut.BLOQUE) {
                        membre.setStatutMembre(com.tontine.enums.MembreStatut.BLOQUE);
                        membre.setActif(false);

                        notificationService.creerNotification(
                                membre.getUtilisateur(), tontine,
                                "Compte bloqué",
                                "Vous avez été bloqué dans la tontine « " + tontine.getNom()
                                        + " » suite à " + membre.getNombreRetards() + " retards. "
                                        + "Contactez l'administrateur pour régulariser.",
                                NotificationType.RETARD_PAIEMENT);

                        notificationService.creerNotification(
                                tontine.getCreateur(), tontine,
                                "Membre bloqué automatiquement",
                                membre.getUtilisateur().getPrenom() + " " + membre.getUtilisateur().getNom()
                                        + " a été bloqué automatiquement (" + membre.getNombreRetards()
                                        + " retards) dans " + tontine.getNom() + ".",
                                NotificationType.RETARD_PAIEMENT);

                        log.info("[Scheduler] Blocage auto: {} dans {} ({} retards)",
                                membre.getUtilisateur().getTelephone(), tontine.getNom(), membre.getNombreRetards());
                    } else {
                        notificationService.creerNotification(
                                membre.getUtilisateur(), tontine,
                                "Cotisation en retard",
                                "Votre cotisation pour " + tontine.getNom()
                                        + " n'a pas été reçue. Contactez l'administrateur.",
                                NotificationType.RETARD_PAIEMENT);

                        notificationService.creerNotification(
                                tontine.getCreateur(), tontine,
                                "Membre en retard",
                                membre.getUtilisateur().getPrenom() + " " + membre.getUtilisateur().getNom()
                                        + " n'a pas cotisé pour le cycle " + cycleVise
                                        + " de " + tontine.getNom() + ".",
                                NotificationType.RETARD_PAIEMENT);
                    }

                    membreRepository.save(membre);
                    log.info("[Scheduler] Retard marqué: {} dans {}",
                            membre.getUtilisateur().getTelephone(), tontine.getNom());

                    // Un retard dégrade le score : recalcule immédiatement le cache —
                    // déclenche la notification SCORE_CRITIQUE si passage au niveau FAIBLE
                    try {
                        scoreFiabiliteService.rafraichirScoreUtilisateur(membre.getUtilisateur().getId());
                    } catch (Exception e) {
                        log.warn("[Scheduler] Rafraîchissement score impossible pour user {} : {}",
                                membre.getUtilisateur().getId(), e.getMessage());
                    }
                }
            }
        }
    }

    // ── 5. Suivi renforcé : rappel quotidien pour les membres au score FAIBLE ──
    //
    // Un membre au score critique n'ayant pas payé le cycle courant reçoit un rappel
    // CHAQUE JOUR (10h) — les autres membres ne reçoivent que les rappels standards
    // (J-3, échéance, 2/3 du cycle). C'est le suivi rapproché des profils à risque.
    //
    @Scheduled(cron = "0 0 10 * * ?")
    @SchedulerLock(name = "rappelRenforceScoreCritique", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    @Transactional(readOnly = true)
    public void rappelRenforceScoreCritique() {
        LocalDateTime debutJour = LocalDate.now().atStartOfDay();

        for (Tontine tontine : tontineRepository.findToutesActives()) {
            membreRepository.findByTontineIdAndActifTrue(tontine.getId()).stream()
                .filter(m -> !aPayeCycle(m, tontine))
                .filter(m -> scoreFiabiliteRepository.findByUtilisateurId(m.getUtilisateur().getId())
                        .map(s -> "FAIBLE".equals(s.getNiveauConfiance()))
                        .orElse(false))
                // Anti-doublon : un seul rappel renforcé par jour et par tontine
                .filter(m -> !notificationRepository.existsByUtilisateurIdAndReferenceIdAndTypeAndCreatedAtAfter(
                        m.getUtilisateur().getId(), tontine.getId(),
                        NotificationType.SCORE_CRITIQUE, debutJour))
                .forEach(m -> {
                    String msg = "Votre cotisation de " + tontine.getMontantContribution() + " "
                            + tontine.getDevise() + " pour " + tontine.getNom()
                            + " (cycle " + tontine.getCycleActuel() + ") est attendue. "
                            + "Payer à temps fera remonter votre score de fiabilité.";
                    notificationService.creerNotification(m.getUtilisateur(), tontine,
                            "⏰ Rappel important — cotisation attendue", msg,
                            NotificationType.SCORE_CRITIQUE);
                });
            log.info("[Scheduler] Rappels renforcés (score FAIBLE) pour tontine: {}", tontine.getNom());
        }
    }

    private boolean aPayeCycle(MembreTontine membre, Tontine tontine) {
        return cotisationRepository
                .findByMembreIdAndTontineIdAndNumeroCycle(
                        membre.getId(), tontine.getId(), tontine.getCycleActuel())
                .filter(c -> c.getStatut() == PaiementStatus.PAYE)
                .isPresent();
    }
}
