package com.tontine.service.impl;

import com.tontine.dto.response.ScoreFiabiliteResponse;
import com.tontine.entity.MembreTontine;
import com.tontine.entity.ScoreFiabilite;
import com.tontine.entity.Utilisateur;
import com.tontine.enums.MembreTontineRole;
import com.tontine.exception.ForbiddenException;
import com.tontine.exception.ResourceNotFoundException;
import com.tontine.enums.NotificationType;
import com.tontine.repository.*;
import com.tontine.service.NotificationService;
import com.tontine.service.ScoreFiabiliteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

/**
 * Adashe Score — score de fiabilité communautaire.
 *
 * Le score 0-100 est calculé par des règles pondérées déterministes (jamais par
 * l'IA). L'IA (Spring AI / Gemini) génère uniquement l'explication en langage
 * simple et la recommandation au créateur. Résultat mis en cache en base :
 * l'IA n'est rappelée que si les données sources changent ou après 24 h.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreFiabiliteServiceImpl implements ScoreFiabiliteService {

    private final MembreTontineRepository membreRepository;
    private final CotisationRepository cotisationRepository;
    private final TirageLitigeRepository tirageLitigeRepository;
    private final ScoreFiabiliteRepository scoreRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;
    private final ObjectProvider<ChatClient> chatClientProvider;

    private static final int CACHE_VALIDITE_HEURES = 24;

    /** Réponse structurée attendue de l'IA. */
    record ScoreAnalyse(String explication, String recommandation) {}

    /** Statistiques agrégées servant d'entrée au calcul et au prompt. */
    record StatsMembre(int nombreTontines, int cotisationsPayees, int cotisationsEnRetard,
                       BigDecimal totalAmendes, int nombreLitiges, int ancienneteMois) {}

    @Override
    @Transactional
    public ScoreFiabiliteResponse getScoreMembre(Long tontineId, Long membreId, Long demandeurId) {
        verifierEstAdmin(tontineId, demandeurId);

        MembreTontine membre = membreRepository.findById(membreId)
                .orElseThrow(() -> new ResourceNotFoundException("Membre non trouvé"));
        if (!membre.getTontine().getId().equals(tontineId)) {
            throw new ResourceNotFoundException("Membre non trouvé dans cette tontine");
        }

        return construireReponse(membre.getUtilisateur(), membre.getId());
    }

    @Override
    @Transactional
    public ScoreFiabiliteResponse getScorePreview(Long tontineId, String telephone, Long demandeurId) {
        verifierEstAdmin(tontineId, demandeurId);

        // Même recherche que l'invitation (ajouterMembre) — le preview reflète l'invitation
        Utilisateur utilisateur = utilisateurRepository.findByTelephone(telephone)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun compte avec ce numéro: " + telephone));

        return construireReponse(utilisateur, null);   // membreId null : pas encore membre
    }

    /** Calcul + cache + IA — partagé entre le flux membre existant et le preview par téléphone. */
    private ScoreFiabiliteResponse construireReponse(Utilisateur utilisateur, Long membreId) {
        StatsMembre stats = agregerStats(utilisateur.getId());
        int score = calculerScore(stats);
        String niveau = niveauConfiance(score);
        String hash = hashStats(stats, score);

        ScoreFiabilite cache = scoreRepository.findByUtilisateurId(utilisateur.getId()).orElse(null);
        if (cache == null || !hash.equals(cache.getDonneesHash()) || estExpire(cache)) {
            cache = rafraichir(cache, utilisateur, stats, score, niveau, hash);
        }

        return ScoreFiabiliteResponse.builder()
                .membreId(membreId)
                .utilisateurId(utilisateur.getId())
                .nomComplet(utilisateur.getPrenom() + " " + utilisateur.getNom())
                .avatarId(utilisateur.getAvatarId())
                .score(cache.getScore())
                .niveauConfiance(cache.getNiveauConfiance())
                .explication(cache.getExplication())
                .recommandation(cache.getRecommandation())
                .nombreTontines(stats.nombreTontines())
                .cotisationsPayees(stats.cotisationsPayees())
                .cotisationsEnRetard(stats.cotisationsEnRetard())
                .nombreLitiges(stats.nombreLitiges())
                .ancienneteMois(stats.ancienneteMois())
                .dateCalcul(cache.getUpdatedAt() != null ? cache.getUpdatedAt() : cache.getCreatedAt())
                .build();
    }

    // ── Agrégation ────────────────────────────────────────────────────────────

    private StatsMembre agregerStats(Long userId) {
        List<MembreTontine> adhesions = membreRepository.findByUtilisateurId(userId);
        int anciennete = adhesions.stream()
                .map(MembreTontine::getDateAdhesion)
                .filter(d -> d != null)
                .min(LocalDateTime::compareTo)
                .map(d -> (int) ChronoUnit.MONTHS.between(d, LocalDateTime.now()))
                .orElse(0);

        // Retards = nombreRetards cumulés (cycles non payés au moment du tirage,
        // rattrapés OU NON) — la même source que le scheduler et les stats tontine.
        // L'ancien comptage via cotisation.estEnRetard ignorait les cycles jamais
        // rattrapés : un membre qui ne régularisait jamais avait un meilleur score
        // que celui qui payait son amende.
        int retards = adhesions.stream().mapToInt(MembreTontine::getNombreRetards).sum();

        return new StatsMembre(
                adhesions.size(),
                cotisationRepository.countPayeesByUtilisateurId(userId),
                retards,
                cotisationRepository.sumAmendesByUtilisateurId(userId),
                tirageLitigeRepository.countByBeneficiaireUtilisateurId(userId),
                anciennete);
    }

    // ── Score par règles pondérées (déterministe, jamais par l'IA) ───────────
    //    ponctualité 40 pts · volume d'historique 25 pts · litiges 20 pts · ancienneté 15 pts

    int calculerScore(StatsMembre s) {
        // Nouveau membre sans aucun historique (ni paiement ni retard) : score neutre
        if (s.cotisationsPayees() == 0 && s.cotisationsEnRetard() == 0) {
            return 40;
        }

        // Chaque cycle raté (rattrapé ou non) pèse contre les cycles payés.
        // payees / (payees + retards) reste borné [0,1] même si retards > payees
        // (cycles jamais rattrapés — aucune cotisation n'existe pour eux).
        double ponctualite = 40.0 * s.cotisationsPayees()
                / (s.cotisationsPayees() + s.cotisationsEnRetard());

        // 24 cotisations payées (≈ 2 ans en mensuel) = plein score de volume
        double volume = 25.0 * Math.min(s.cotisationsPayees(), 24) / 24.0;

        double litiges = Math.max(0.0, 20.0 - 10.0 * s.nombreLitiges());

        // 18 mois d'ancienneté = plein score
        double anciennete = 15.0 * Math.min(s.ancienneteMois(), 18) / 18.0;

        return (int) Math.round(ponctualite + volume + litiges + anciennete);
    }

    static String niveauConfiance(int score) {
        if (score >= 70) return "ELEVE";
        if (score >= 45) return "MOYEN";
        return "FAIBLE";
    }

    // ── Cache + IA ────────────────────────────────────────────────────────────

    private boolean estExpire(ScoreFiabilite cache) {
        LocalDateTime ref = cache.getUpdatedAt() != null ? cache.getUpdatedAt() : cache.getCreatedAt();
        return ref == null || ref.isBefore(LocalDateTime.now().minusHours(CACHE_VALIDITE_HEURES));
    }

    /** Analyse + provenance : viaIa=false signifie que l'explication de secours a été utilisée. */
    record AnalyseResultat(ScoreAnalyse analyse, boolean viaIa) {}

    private ScoreFiabilite rafraichir(ScoreFiabilite existant, Utilisateur utilisateur,
                                      StatsMembre stats, int score, String niveau, String hash) {
        AnalyseResultat resultat = genererAnalyse(utilisateur.getPrenom(), stats, score, niveau);

        // Détection de transition AVANT écrasement — une seule alerte par dégradation
        String ancienNiveau = existant != null ? existant.getNiveauConfiance() : null;
        boolean devientCritique = "FAIBLE".equals(niveau) && !"FAIBLE".equals(ancienNiveau);

        ScoreFiabilite entite = existant != null ? existant
                : ScoreFiabilite.builder().utilisateur(utilisateur).build();
        entite.setScore(score);
        entite.setNiveauConfiance(niveau);
        entite.setExplication(resultat.analyse().explication());
        entite.setRecommandation(resultat.analyse().recommandation());
        entite.setDonneesHash(hash);
        entite.setModeleIa(resultat.viaIa() ? "gemini-2.5-flash" : null);
        ScoreFiabilite saved = scoreRepository.save(entite);

        if (devientCritique) notifierScoreCritique(utilisateur, score);
        return saved;
    }

    /**
     * Score passé à FAIBLE : alerte les créateurs des tontines actives du membre
     * (outil de décision) et le membre lui-même (transparence + motivation).
     */
    private void notifierScoreCritique(Utilisateur utilisateur, int score) {
        try {
            List<MembreTontine> adhesionsActives = membreRepository.findByUtilisateurId(utilisateur.getId())
                    .stream().filter(m -> Boolean.TRUE.equals(m.getActif())).toList();
            if (adhesionsActives.isEmpty()) return;

            String prenom = utilisateur.getPrenom();

            // Créateurs distincts (hors le membre lui-même)
            adhesionsActives.stream()
                    .filter(m -> !m.getTontine().getCreateur().getId().equals(utilisateur.getId()))
                    .collect(java.util.stream.Collectors.toMap(
                            m -> m.getTontine().getCreateur().getId(), m -> m, (a, b) -> a))
                    .values()
                    .forEach(m -> notificationService.creerNotification(
                            m.getTontine().getCreateur(), m.getTontine(),
                            "⚠️ Fiabilité en baisse — " + prenom,
                            "Le score de fiabilité de " + prenom + " est passé à FAIBLE (" + score
                                    + "/100). Consultez sa fiche et suivez ses paiements de près.",
                            NotificationType.SCORE_CRITIQUE));

            // Le membre est aussi prévenu — il saura pourquoi il reçoit plus de rappels
            MembreTontine premiere = adhesionsActives.get(0);
            notificationService.creerNotification(utilisateur, premiere.getTontine(),
                    "Votre score de fiabilité a baissé",
                    "Votre Adashe Score est passé à FAIBLE (" + score + "/100). "
                            + "Payez vos cotisations à temps pour le faire remonter.",
                    NotificationType.SCORE_CRITIQUE);
        } catch (Exception e) {
            log.warn("Notification score critique impossible : {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void rafraichirScoreUtilisateur(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId).orElse(null);
        if (utilisateur == null) return;
        // Recalcule et rafraîchit le cache si les données ont changé — déclenche
        // la notification SCORE_CRITIQUE en cas de passage au niveau FAIBLE.
        construireReponse(utilisateur, null);
    }

    private AnalyseResultat genererAnalyse(String prenom, StatsMembre stats, int score, String niveau) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return new AnalyseResultat(analyseSecours(stats, score), false);
        }
        try {
            ScoreAnalyse analyse = chatClient.prompt()
                    .user(u -> u.text("""
                            Membre : {prenom}
                            Score de fiabilité calculé : {score}/100 (niveau {niveau})
                            Nombre de tontines : {tontines}
                            Cotisations payées : {payees}
                            Cotisations en retard : {retards}
                            Total des amendes : {amendes} FCFA
                            Litiges de tirage le concernant : {litiges}
                            Ancienneté dans l'application : {anciennete} mois

                            Génère l'explication et la recommandation.
                            """)
                            .param("prenom", prenom)
                            .param("score", score)
                            .param("niveau", niveau)
                            .param("tontines", stats.nombreTontines())
                            .param("payees", stats.cotisationsPayees())
                            .param("retards", stats.cotisationsEnRetard())
                            .param("amendes", stats.totalAmendes())
                            .param("litiges", stats.nombreLitiges())
                            .param("anciennete", stats.ancienneteMois()))
                    .call()
                    .entity(ScoreAnalyse.class);
            if (analyse == null || analyse.explication() == null || analyse.explication().isBlank()) {
                return new AnalyseResultat(analyseSecours(stats, score), false);
            }
            return new AnalyseResultat(analyse, true);
        } catch (Exception e) {
            log.warn("Analyse IA indisponible, utilisation de l'explication de secours : {}", e.getMessage());
            return new AnalyseResultat(analyseSecours(stats, score), false);
        }
    }

    /** Explication générique si l'IA est indisponible — l'app ne casse jamais à cause de l'IA. */
    private ScoreAnalyse analyseSecours(StatsMembre stats, int score) {
        String explication = stats.cotisationsPayees() == 0
                ? "Ce membre est nouveau sur AdasheCash et n'a pas encore d'historique de cotisations."
                : String.format("Ce membre a payé %d cotisation(s) dont %d en retard, dans %d tontine(s), avec %d litige(s).",
                        stats.cotisationsPayees(), stats.cotisationsEnRetard(),
                        stats.nombreTontines(), stats.nombreLitiges());
        String recommandation = score >= 70 ? "Profil fiable : vous pouvez l'accepter en confiance."
                : score >= 45 ? "Profil correct : acceptez avec un suivi régulier des paiements."
                : "Historique limité ou fragile : soyez vigilant et privilégiez un petit montant au départ.";
        return new ScoreAnalyse(explication, recommandation);
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private void verifierEstAdmin(Long tontineId, Long userId) {
        MembreTontine m = membreRepository.findByUtilisateurIdAndTontineId(userId, tontineId)
                .orElseThrow(() -> new ForbiddenException("Vous n'êtes pas membre de cette tontine"));
        if (m.getRoleMembreTontine() == MembreTontineRole.MEMBRE) {
            throw new ForbiddenException("Le score de fiabilité est réservé aux administrateurs de la tontine");
        }
    }

    private String hashStats(StatsMembre stats, int score) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String source = stats.toString() + "|" + score;
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return String.valueOf((stats.toString() + score).hashCode());
        }
    }
}
