package com.tontine.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.tontine.config.DeveloppeurCompteConfig;
import com.tontine.entity.CompteWallet;
import com.tontine.entity.Tirage;
import com.tontine.entity.VirementCommission;
import com.tontine.entity.VirementCommission.TypeBeneficiaire;
import com.tontine.enums.PaiementMode;
import com.tontine.enums.VirementAmendeStatut;
import com.tontine.gateway.MonetbilGateway;
import com.tontine.repository.CompteWalletRepository;
import com.tontine.repository.VirementCommissionRepository;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gère les virements de commission prélevés à chaque tirage confirmé.
 * Flux : 3/4 → wallet actif de l'admin, 1/4 → compte Adashe.
 * Même pattern async/afterCommit que VirementAmendeService.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VirementCommissionService {

    private final VirementCommissionRepository virementCommissionRepository;
    private final CompteWalletRepository compteWalletRepository;
    private final MonetbilGateway monetbilGateway;
    private final DeveloppeurCompteConfig developpeurCompte;

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
     * Crée 2 lignes VirementCommission (EN_ATTENTE) dans la transaction appelante.
     * Appelé depuis confirmerTirage() avant le commit — les IDs sont ensuite
     * dispatché via afterCommit → effectuerVirementsAsyncParIds.
     *
     * @return liste des IDs créés (0, 1 ou 2 éléments selon les wallets disponibles)
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

        // 3/4 pour l'admin, 1/4 pour Adashe (arrondi inférieur pour Adashe)
        BigDecimal partAdmin  = commission.multiply(BigDecimal.valueOf(3))
                .divide(BigDecimal.valueOf(4), 0, RoundingMode.FLOOR);
        BigDecimal partAdashe = commission.subtract(partAdmin);

        List<Long> ids = new ArrayList<>();

        if (partAdmin.compareTo(BigDecimal.ZERO) > 0) {
            VirementCommission vc = VirementCommission.builder()
                    .tirage(tirage)
                    .typeBeneficiaire(TypeBeneficiaire.ADMIN)
                    .montant(partAdmin)
                    .operateur(operateur)
                    .numeroBeneficiaire(normaliserTel(walletAdmin.getTelephone()))
                    .statut(VirementAmendeStatut.EN_ATTENTE)
                    .build();
            ids.add(virementCommissionRepository.save(vc).getId());
        }

        if (partAdashe.compareTo(BigDecimal.ZERO) > 0) {
            String numAdashe = resoudreNumeroAdashe(operateur);
            VirementCommission vc = VirementCommission.builder()
                    .tirage(tirage)
                    .typeBeneficiaire(TypeBeneficiaire.ADASHE)
                    .montant(partAdashe)
                    .operateur(operateur)
                    .numeroBeneficiaire(numAdashe)
                    .statut(VirementAmendeStatut.EN_ATTENTE)
                    .build();
            ids.add(virementCommissionRepository.save(vc).getId());
        }

        log.info("[VirementCommission] Tirage {} — {} XAF planifiés ({} admin + {} Adashe) via {}",
                tirage.getId(), commission, partAdmin, partAdashe, operateur);
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
        return virementCommissionRepository.save(vc);
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

    private String resoudreNumeroAdashe(PaiementMode operateur) {
        String raw = (operateur == PaiementMode.MTN_MOBILE_MONEY)
                ? developpeurCompte.getMtnMomo()
                : developpeurCompte.getOrangeMoney();
        return normaliserTel(raw);
    }

    private String normaliserTel(String telephone) {
        String t = telephone.replaceAll("[^0-9]", "");
        if (t.startsWith("237")) t = t.substring(3);
        return "237" + t;
    }

    private String signerRequete(MultiValueMap<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder(monetbilServiceSecret);
        params.entrySet().stream()
                .filter(e -> !"sign".equals(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getValue().get(0)));

        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(
                monetbilServiceSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
