package com.tontine.scheduler;

import com.tontine.entity.MembreTontine;
import com.tontine.entity.Tontine;
import com.tontine.enums.NotificationType;
import com.tontine.repository.CotisationRepository;
import com.tontine.repository.MembreTontineRepository;
import com.tontine.repository.TontineRepository;
import com.tontine.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Briefing hebdomadaire de l'admin — dimanche 18h.
 *
 * Pour chaque tontine active : agrège l'activité de la semaine (collecte, état du
 * cycle, retardataires) puis demande à Gemini un résumé actionnable en 3 phrases,
 * envoyé au créateur en push. Si l'IA est indisponible, un résumé template prend
 * le relais — le briefing part toujours. Coût : 1 appel IA / tontine / semaine.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BriefingHebdoScheduler {

    private final TontineRepository       tontineRepository;
    private final MembreTontineRepository membreRepository;
    private final CotisationRepository    cotisationRepository;
    private final NotificationService     notificationService;
    private final ObjectProvider<ChatClient> chatClientProvider;

    private static final String SYSTEM_BRIEFING = """
            Tu rédiges le briefing hebdomadaire de l'administrateur d'une tontine
            camerounaise sur AdasheCash. À partir des chiffres fournis, écris un
            résumé en français simple : 3 phrases maximum, puis si nécessaire UNE
            action concrète à faire cette semaine (commençant par "À faire : ").
            Ton chaleureux et direct. Montants en FCFA. N'invente aucun chiffre.
            Réponds uniquement avec le texte du briefing, sans préambule.
            """;

    @Scheduled(cron = "0 0 18 * * SUN")
    @SchedulerLock(name = "briefingHebdo", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    @Transactional(readOnly = true)
    public void envoyerBriefings() {
        LocalDate ilYaUneSemaine = LocalDate.now().minusDays(7);

        for (Tontine tontine : tontineRepository.findToutesActives()) {
            try {
                List<MembreTontine> membres = membreRepository.findByTontineIdAndActifTrue(tontine.getId());
                if (membres.isEmpty()) continue;

                Set<Long> payes = cotisationRepository
                        .findMembreIdsAyantPayePourCycle(tontine.getId(), tontine.getCycleActuel());
                List<String> retardataires = membres.stream()
                        .filter(m -> !payes.contains(m.getId()))
                        .map(m -> m.getUtilisateur().getPrenom())
                        .limit(3)
                        .collect(Collectors.toList());
                BigDecimal collecteSemaine = cotisationRepository
                        .sumMontantPayeDepuis(tontine.getId(), ilYaUneSemaine);

                String texte = genererBriefing(tontine, membres.size(), payes.size(),
                        retardataires, collecteSemaine);

                notificationService.creerNotification(tontine.getCreateur(), tontine,
                        "📋 Briefing hebdo — " + tontine.getNom(),
                        texte, NotificationType.BRIEFING_HEBDO);
                log.info("[Briefing] Envoyé pour tontine {} ({})", tontine.getId(), tontine.getNom());
            } catch (Exception e) {
                log.warn("[Briefing] Échec pour tontine {} : {}", tontine.getId(), e.getMessage());
            }
        }
    }

    private String genererBriefing(Tontine tontine, int nbMembres, int nbPayes,
                                   List<String> retardataires, BigDecimal collecteSemaine) {
        String fallback = briefingSecours(tontine, nbMembres, nbPayes, retardataires, collecteSemaine);

        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) return fallback;

        try {
            String texte = chatClient.prompt()
                    .system(SYSTEM_BRIEFING)
                    .user(u -> u.text("""
                            Tontine : {nom}
                            Cycle en cours : {cycle}
                            Membres actifs : {membres}
                            Ont payé ce cycle : {payes}
                            N'ont pas encore payé : {retardataires}
                            Collecté ces 7 derniers jours : {collecte} FCFA
                            Prochain tirage : {prochain}
                            """)
                            .param("nom", tontine.getNom())
                            .param("cycle", tontine.getCycleActuel())
                            .param("membres", nbMembres)
                            .param("payes", nbPayes)
                            .param("retardataires", retardataires.isEmpty() ? "personne" : String.join(", ", retardataires))
                            .param("collecte", collecteSemaine)
                            .param("prochain", tontine.getDateProchainCycle() != null
                                    ? tontine.getDateProchainCycle().toString() : "non planifié"))
                    .call()
                    .content();
            return (texte == null || texte.isBlank()) ? fallback : texte.trim();
        } catch (Exception e) {
            log.warn("[Briefing] IA indisponible, fallback template : {}", e.getMessage());
            return fallback;
        }
    }

    /** Résumé template si l'IA est indisponible — le briefing part toujours. */
    private String briefingSecours(Tontine tontine, int nbMembres, int nbPayes,
                                   List<String> retardataires, BigDecimal collecteSemaine) {
        StringBuilder sb = new StringBuilder();
        sb.append("Cette semaine : ").append(nbPayes).append("/").append(nbMembres)
          .append(" membres ont payé le cycle ").append(tontine.getCycleActuel())
          .append(", ").append(collecteSemaine).append(" FCFA collectés ces 7 derniers jours.");
        if (!retardataires.isEmpty()) {
            sb.append(" À faire : relancer ").append(String.join(", ", retardataires)).append(".");
        }
        return sb.toString();
    }
}
