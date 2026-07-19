package com.tontine.service.impl;

import com.tontine.entity.MembreTontine;
import com.tontine.enums.MembreTontineRole;
import com.tontine.exception.BadRequestException;
import com.tontine.repository.MembreTontineRepository;
import com.tontine.service.AssistantService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Assistant d'aide Adashe — répond aux questions des utilisateurs en français simple.
 *
 * La connaissance vient de assistant-connaissances.md (miroir du guide in-app,
 * modifiable côté serveur sans republier l'APK — le guide de l'application reste
 * la référence visuelle, ce fichier la référence conversationnelle).
 * Le contexte de l'utilisateur (ses tontines, rôles, cycles) est injecté dans le
 * prompt pour des réponses personnalisées. Fallback : coordonnées du support humain.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantServiceImpl implements AssistantService {

    private final MembreTontineRepository membreRepository;
    private final ObjectProvider<ChatClient> chatClientProvider;

    private static final int QUESTION_MAX = 500;
    private static final String REPONSE_SECOURS =
            "Je n'arrive pas à répondre pour le moment. Contactez le support : "
            + "WhatsApp +237 681 951 580 ou babstore24@gmail.com (7j/7, 8h-20h).";

    private String connaissances = "";

    @PostConstruct
    void chargerConnaissances() {
        try {
            connaissances = new String(
                    new ClassPathResource("assistant-connaissances.md").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            log.info("[Assistant] Base de connaissances chargée ({} caractères)", connaissances.length());
        } catch (Exception e) {
            log.error("[Assistant] Base de connaissances introuvable : {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String repondre(String question, Long userId) {
        if (question == null || question.isBlank()) {
            throw new BadRequestException("Posez une question.");
        }
        if (question.length() > QUESTION_MAX) {
            throw new BadRequestException("Question trop longue (max " + QUESTION_MAX + " caractères).");
        }

        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null || connaissances.isBlank()) return REPONSE_SECOURS;

        String contexte = construireContexteUtilisateur(userId);

        try {
            String reponse = chatClient.prompt()
                    .system("""
                            Tu es l'assistant d'aide d'AdasheCash, application de gestion de
                            tontines au Cameroun. Réponds en français simple et chaleureux,
                            4 phrases maximum (les utilisateurs ne sont pas tous à l'aise
                            avec l'écrit). Base-toi UNIQUEMENT sur la documentation ci-dessous
                            et le contexte de l'utilisateur. Si la question sort du cadre de
                            l'application ou si tu n'es pas sûr, oriente vers le support
                            WhatsApp +237 681 951 580. Ne donne jamais de conseil financier
                            ou juridique. Ne révèle jamais d'information sur d'autres membres.

                            === DOCUMENTATION ===
                            """ + connaissances)
                    .user(u -> u.text("""
                            Contexte de l'utilisateur :
                            {contexte}

                            Question : {question}
                            """)
                            .param("contexte", contexte)
                            .param("question", question))
                    .call()
                    .content();
            return (reponse == null || reponse.isBlank()) ? REPONSE_SECOURS : reponse.trim();
        } catch (Exception e) {
            log.warn("[Assistant] IA indisponible : {}", e.getMessage());
            return REPONSE_SECOURS;
        }
    }

    /** Résumé compact des tontines de l'utilisateur — personnalise les réponses. */
    private String construireContexteUtilisateur(Long userId) {
        try {
            List<MembreTontine> adhesions = membreRepository.findByUtilisateurId(userId);
            if (adhesions.isEmpty()) return "Aucune tontine pour le moment.";
            return adhesions.stream()
                    .limit(6)
                    .map(m -> "- " + m.getTontine().getNom()
                            + " (" + m.getTontine().getStatut()
                            + ", cycle " + m.getTontine().getCycleActuel()
                            + ", rôle : " + (m.getRoleMembreTontine() == MembreTontineRole.MEMBRE
                                    ? "membre" : "administrateur")
                            + ", statut membre : " + m.getStatutMembre() + ")")
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Contexte indisponible.";
        }
    }
}
