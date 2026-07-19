package com.tontine.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.tontine.entity.CompteWallet;
import com.tontine.entity.Tirage;
import com.tontine.entity.VirementCommission;
import com.tontine.entity.VirementCommission.TypeBeneficiaire;
import com.tontine.enums.NotificationType;
import com.tontine.enums.PaiementMode;
import com.tontine.enums.VirementAmendeStatut;
import com.tontine.gateway.MonetbilGateway;
import com.tontine.repository.CompteWalletRepository;
import com.tontine.repository.VirementCommissionRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gère les virements de commission prélevés à chaque tirage confirmé.
 * Flux : 100% → wallet actif de l'admin (créateur de la tontine).
 * Même pattern async/afterCommit que VirementAmendeService.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VirementCommissionService {

    private final VirementCommissionRepository virementCommissionRepository;
    private final CompteWalletRepository compteWalletRepository;
    private final MonetbilGateway monetbilGateway;
    private final NotificationService notificationService;

    @Lazy
    @Autowired
    private VirementCommissionService self;

    @Value("${monetbil.service-key}")
    private String monetbilServiceKey;

    @Value("${monetbil.service-secret}")
    private String monetbilServiceSecret;

    @Value("${monetbil.transfer-url:https://api.monetbil.com/transfer/v1/process}")
    private String monetbilTransferUrl;

    /**
     * Crée la ligne VirementCommission (EN_ATTENTE) dans la transaction appelante.
     * Appelé depuis confirmerTirage() avant le commit — les IDs sont ensuite
     * dispatché via afterCommit → effectuerVirementsAsyncParIds.
     *
     * @return liste des IDs créés (0 ou 1 élément selon les wallets disponibles)
     */
    @Transactional
    public List<Long> creerVirementsEnAttente(Tirage tirage) {
        BigDecimal commission = tirage.getCommissionPrelevee();
        if (commission == null || commission.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        Long adminId = tirage.getTontine().getCreateur().getId();
        List<CompteWallet> wallets = compteWalletRepository.findActifsByUtilisateurId(adminId);
        if (wallets.isEmpty()) {
            log.warn("[VirementCommission] Tirage {} : admin {} n'a aucun wallet actif — commission non virée",
                    tirage.getId(), adminId);
            return List.of();
        }

        // Préférer MTN, sinon Orange
        CompteWallet walletAdmin = wallets.stream()
                .filter(w -> w.getOperateur() == PaiementMode.MTN_MOBILE_MONEY)
                .findFirst()
                .orElse(wallets.get(0));

        PaiementMode operateur = walletAdmin.getOperateur();

        // 100% de la commission pour l'admin
        List<Long> ids = new ArrayList<>();

        VirementCommission vc = VirementCommission.builder()
                .tirage(tirage)
                .typeBeneficiaire(TypeBeneficiaire.ADMIN)
                .montant(commission)
                .operateur(operateur)
                .numeroBeneficiaire(normaliserTel(walletAdmin.getTelephone()))
                .statut(VirementAmendeStatut.EN_ATTENTE)
                .build();
        ids.add(virementCommissionRepository.save(vc).getId());

        log.info("[VirementCommission] Tirage {} — {} XAF planifiés (100% admin) via {}",
                tirage.getId(), commission, operateur);
        return ids;
    }

    /**
     * Exécute les virements Monetbil en arrière-plan, après commit de la TX appelante.
     * Chaque virement est indépendant : un échec n'annule pas les autres.
     */
    @Async("notifExecutor")
    public void effectuerVirementsAsyncParIds(List<Long> ids) {
        for (Long id : ids) {
            VirementCommission vc = virementCommissionRepository.findById(id).orElse(null);
            if (vc == null || vc.getStatut() != VirementAmendeStatut.EN_ATTENTE) continue;
            String ref = "COMM-" + vc.getTirage().getId() + "-" + vc.getTypeBeneficiaire().name() + "-" + id;
            executerTransfert(vc, ref);
            self.enregistrerResultat(vc);
        }
    }

    @Transactional
    public VirementCommission enregistrerResultat(VirementCommission vc) {
        VirementCommission saved = virementCommissionRepository.save(vc);
        // Notifier l'admin (bénéficiaire de la commission) — dans la TX pour les lazy loads
        try {
            if (saved.getTypeBeneficiaire() == TypeBeneficiaire.ADMIN) {
                var tontine = saved.getTirage().getTontine();
                var admin   = tontine.getCreateur();
                if (saved.getStatut() == VirementAmendeStatut.SUCCES) {
                    notificationService.creerNotification(admin, tontine,
                            "💰 Commission virée",
                            "Votre commission de " + saved.getMontant() + " FCFA ("
                                    + tontine.getNom() + ") a été envoyée sur votre Mobile Money.",
                            NotificationType.VIREMENT_RECU);
                } else if (saved.getStatut() == VirementAmendeStatut.ECHEC) {
                    notificationService.creerNotification(admin, tontine,
                            "⚠️ Virement de commission échoué",
                            "Le virement de votre commission (" + saved.getMontant() + " FCFA, "
                                    + tontine.getNom() + ") a échoué : "
                                    + (saved.getMessageErreur() != null ? saved.getMessageErreur() : "erreur inconnue"),
                            NotificationType.VIREMENT_ECHEC);
                }
            }
        } catch (Exception e) {
            log.warn("[VirementCommission] Notification impossible : {}", e.getMessage());
        }
        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void executerTransfert(VirementCommission vc, String reference) {
        try {
            MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
            payload.add("service",  monetbilServiceKey);
            payload.add("amount",   String.valueOf(vc.getMontant().longValue()));
            payload.add("phone",    vc.getNumeroBeneficiaire());
            payload.add("item_ref", reference);
            payload.add("sign",     signerRequete(payload));

            log.info("[VirementCommission] Transfert {} XAF → {} ({}) ref={}",
                    vc.getMontant(), vc.getNumeroBeneficiaire(), vc.getTypeBeneficiaire(), reference);

            JsonNode response = monetbilGateway.callApi(monetbilTransferUrl, payload);
            int success = response.path("success").asInt(0);

            if (success == 1) {
                vc.setStatut(VirementAmendeStatut.SUCCES);
                vc.setReferenceTransfert(response.path("transaction_id").asText(""));
                vc.setDateVirement(LocalDateTime.now());
                log.info("[VirementCommission] SUCCES — {} XAF → {} ({})",
                        vc.getMontant(), vc.getNumeroBeneficiaire(), reference);
            } else {
                String msg = response.path("message").asText("Échec inconnu Monetbil");
                vc.setStatut(VirementAmendeStatut.ECHEC);
                vc.setMessageErreur(msg);
                log.warn("[VirementCommission] ECHEC — {} : {}", reference, msg);
            }

        } catch (Exception e) {
            vc.setStatut(VirementAmendeStatut.ECHEC);
            vc.setMessageErreur(e.getMessage());
            log.error("[VirementCommission] Erreur transfert {} : {}", reference, e.getMessage());
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
