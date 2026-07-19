package com.tontine.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.tontine.entity.CompteWallet;
import com.tontine.entity.Tirage;
import com.tontine.entity.VirementCagnotte;
import com.tontine.enums.NotificationType;
import com.tontine.enums.PaiementMode;
import com.tontine.enums.VirementAmendeStatut;
import com.tontine.gateway.MonetbilGateway;
import com.tontine.repository.CompteWalletRepository;
import com.tontine.repository.VirementCagnotteRepository;
import com.tontine.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Vire la cagnotte au bénéficiaire après confirmation du tirage via Monetbil Transfer.
 * Pattern identique à VirementCommissionService : TX courte (creerVirementEnAttente)
 * puis appel HTTP hors transaction (effectuerVirementAsyncParId via afterCommit).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VirementCagnotteService {

    private final VirementCagnotteRepository virementCagnotteRepository;
    private final CompteWalletRepository     compteWalletRepository;
    private final MonetbilGateway            monetbilGateway;
    private final NotificationService        notificationService;

    @Lazy
    @Autowired
    private VirementCagnotteService self;

    @Value("${monetbil.service-key}")
    private String monetbilServiceKey;

    @Value("${monetbil.service-secret}")
    private String monetbilServiceSecret;

    @Value("${monetbil.transfer-url:https://api.monetbil.com/transfer/v1/process}")
    private String monetbilTransferUrl;

    /**
     * Crée une ligne VirementCagnotte (EN_ATTENTE) dans la transaction appelante.
     * Cherche le wallet actif du bénéficiaire — si aucun, le virement n'est pas créé
     * et l'admin devra transférer manuellement.
     *
     * @return l'ID créé, ou null si aucun wallet bénéficiaire trouvé
     */
    @Transactional
    public Long creerVirementEnAttente(Tirage tirage) {
        BigDecimal montant = tirage.getMontantDistribue();
        if (montant == null || montant.compareTo(BigDecimal.ZERO) <= 0) return null;

        Long beneficiaireUserId = tirage.getBeneficiaire().getUtilisateur().getId();
        List<CompteWallet> wallets = compteWalletRepository.findActifsByUtilisateurId(beneficiaireUserId);

        if (wallets.isEmpty()) {
            log.warn("[VirementCagnotte] Tirage {} : bénéficiaire {} n'a aucun wallet actif — virement non créé",
                    tirage.getId(), beneficiaireUserId);
            return null;
        }

        // Préférer Orange Money (opérateur principal de la tontine), sinon MTN
        CompteWallet wallet = wallets.stream()
                .filter(w -> w.getOperateur() == PaiementMode.ORANGE_MONEY)
                .findFirst()
                .orElseGet(() -> wallets.stream()
                        .filter(w -> w.getOperateur() == PaiementMode.MTN_MOBILE_MONEY)
                        .findFirst()
                        .orElse(wallets.get(0)));

        VirementCagnotte vc = VirementCagnotte.builder()
                .tirage(tirage)
                .montant(montant)
                .operateur(wallet.getOperateur())
                .numeroBeneficiaire(normaliserTel(wallet.getTelephone()))
                .statut(VirementAmendeStatut.EN_ATTENTE)
                .build();

        Long id = virementCagnotteRepository.save(vc).getId();
        log.info("[VirementCagnotte] Tirage {} — {} XAF planifiés vers {} ({})",
                tirage.getId(), montant, wallet.getTelephone(), wallet.getOperateur());
        return id;
    }

    /**
     * Exécute le transfert Monetbil en arrière-plan, après commit de la TX appelante.
     */
    @Async("notifExecutor")
    public void effectuerVirementAsyncParId(Long id) {
        VirementCagnotte vc = virementCagnotteRepository.findById(id).orElse(null);
        if (vc == null || vc.getStatut() != VirementAmendeStatut.EN_ATTENTE) return;

        String ref = "CAGNOTTE-" + vc.getTirage().getId() + "-" + id;
        executerTransfert(vc, ref);
        self.enregistrerResultat(vc);
    }

    @Transactional
    public VirementCagnotte enregistrerResultat(VirementCagnotte vc) {
        VirementCagnotte saved = virementCagnotteRepository.save(vc);
        // Notifications dans la TX — les relations lazy (tirage → bénéficiaire/tontine) sont chargeables
        try {
            var tirage       = saved.getTirage();
            var beneficiaire = tirage.getBeneficiaire().getUtilisateur();
            var tontine      = tirage.getTontine();
            if (saved.getStatut() == VirementAmendeStatut.SUCCES) {
                notificationService.creerNotification(beneficiaire, tontine,
                        "💰 Cagnotte envoyée",
                        "Votre cagnotte de " + saved.getMontant() + " FCFA (" + tontine.getNom()
                                + ") a été envoyée sur votre compte Mobile Money "
                                + saved.getNumeroBeneficiaire() + ".",
                        NotificationType.VIREMENT_RECU);
            } else if (saved.getStatut() == VirementAmendeStatut.ECHEC) {
                notificationService.creerNotification(beneficiaire, tontine,
                        "⚠️ Virement de cagnotte échoué",
                        "L'envoi de votre cagnotte de " + saved.getMontant() + " FCFA ("
                                + tontine.getNom() + ") a échoué. L'administrateur a été prévenu.",
                        NotificationType.VIREMENT_ECHEC);
                notificationService.creerNotification(tontine.getCreateur(), tontine,
                        "⚠️ Virement de cagnotte échoué",
                        "Le virement de la cagnotte (" + saved.getMontant() + " FCFA) vers "
                                + beneficiaire.getPrenom() + " a échoué : "
                                + (saved.getMessageErreur() != null ? saved.getMessageErreur() : "erreur inconnue")
                                + ". Un transfert manuel peut être nécessaire.",
                        NotificationType.VIREMENT_ECHEC);
            }
        } catch (Exception e) {
            log.warn("[VirementCagnotte] Notification impossible : {}", e.getMessage());
        }
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void executerTransfert(VirementCagnotte vc, String reference) {
        try {
            MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
            payload.add("service",  monetbilServiceKey);
            payload.add("amount",   String.valueOf(vc.getMontant().longValue()));
            payload.add("phone",    vc.getNumeroBeneficiaire());
            payload.add("item_ref", reference);
            payload.add("sign",     signerRequete(payload));

            log.info("[VirementCagnotte] Transfert {} XAF → {} ref={}",
                    vc.getMontant(), vc.getNumeroBeneficiaire(), reference);

            JsonNode response = monetbilGateway.callApi(monetbilTransferUrl, payload);
            int success = response.path("success").asInt(0);

            if (success == 1) {
                vc.setStatut(VirementAmendeStatut.SUCCES);
                vc.setReferenceTransfert(response.path("transaction_id").asText(""));
                vc.setDateVirement(LocalDateTime.now());
                log.info("[VirementCagnotte] SUCCES — {} XAF → {}", vc.getMontant(), vc.getNumeroBeneficiaire());
            } else {
                String msg = response.path("message").asText("Échec inconnu Monetbil");
                vc.setStatut(VirementAmendeStatut.ECHEC);
                vc.setMessageErreur(msg);
                log.warn("[VirementCagnotte] ECHEC — {} : {}", reference, msg);
            }

        } catch (Exception e) {
            vc.setStatut(VirementAmendeStatut.ECHEC);
            vc.setMessageErreur(e.getMessage());
            log.error("[VirementCagnotte] Erreur transfert {} : {}", reference, e.getMessage());
        }
    }

    private String normaliserTel(String telephone) {
        String t = telephone.replaceAll("[^0-9]", "");
        if (t.startsWith("237")) t = t.substring(3);
        return "237" + t;
    }

    // MD5(serviceSecret + valeurs triées par clé, sans "sign") — algorithme officiel Monetbil
    private String signerRequete(MultiValueMap<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder(monetbilServiceSecret);
        params.entrySet().stream()
                .filter(e -> !"sign".equals(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getValue().get(0)));

        byte[] hash = MessageDigest.getInstance("MD5")
                .digest(sb.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
