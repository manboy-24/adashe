package com.tontine.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.tontine.config.DeveloppeurCompteConfig;
import com.tontine.entity.Paiement;
import com.tontine.entity.VirementAmende;
import com.tontine.enums.PaiementMode;
import com.tontine.enums.VirementAmendeStatut;
import com.tontine.exception.BadRequestException;
import com.tontine.exception.ResourceNotFoundException;
import com.tontine.gateway.MonetbilGateway;
import com.tontine.repository.PaiementRepository;
import com.tontine.repository.VirementAmendeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class VirementAmendeService {

    private final VirementAmendeRepository virementAmendeRepository;
    private final PaiementRepository paiementRepository;
    private final MonetbilGateway monetbilGateway;
    private final DeveloppeurCompteConfig developpeurCompte;

    @Lazy
    @Autowired
    private VirementAmendeService self;

    @Value("${monetbil.service-key}")
    private String monetbilServiceKey;

    @Value("${monetbil.service-secret}")
    private String monetbilServiceSecret;

    @Value("${monetbil.transfer-url:https://api.monetbil.com/transfer/v1/process}")
    private String monetbilTransferUrl;

    /**
     * Crée un enregistrement VirementAmende et déclenche immédiatement
     * le transfert Monetbil vers le compte du développeur.
     * Appelé après confirmation d'un paiement Mobile Money avec amende.
     */
    @Transactional
    public void effectuerVirement(Paiement paiement, BigDecimal montantAmende) {
        if (montantAmende == null || montantAmende.compareTo(BigDecimal.ZERO) <= 0) return;

        String numeroBenef = resoudreNumeroDeveloppeur(paiement.getOperateur());
        String ref = "AMENDE-" + paiement.getReferenceTransaction().replace("TONTINE-", "");

        VirementAmende virement = VirementAmende.builder()
                .paiement(paiement)
                .montant(montantAmende)
                .operateur(paiement.getOperateur())
                .numeroBeneficiaire(numeroBenef)
                .referenceTontine(paiement.getReferenceTransaction())
                .statut(VirementAmendeStatut.EN_ATTENTE)
                .build();
        virement = virementAmendeRepository.save(virement);

        executerTransfert(virement, ref);
        virementAmendeRepository.save(virement);
    }

    /**
     * Relance un virement échoué depuis l'interface admin.
     * Pas de @Transactional ici : l'appel HTTP Monetbil se fait hors transaction
     * pour ne pas maintenir un verrou DB pendant toute la durée de la requête réseau.
     */
    public VirementAmende retenterVirement(Long virementId) {
        VirementAmende virement = self.claimerRetry(virementId);
        String ref = "AMENDE-RETRY-" + virement.getId() + "-" + virement.getReferenceTontine().replace("TONTINE-", "");
        executerTransfert(virement, ref);
        return self.enregistrerResultat(virement);
    }

    /** Tx courte 1 : verrou pessimiste + bascule EN_ATTENTE, commit immédiat. */
    @Transactional
    public VirementAmende claimerRetry(Long virementId) {
        VirementAmende virement = virementAmendeRepository.findByIdForUpdate(virementId)
                .orElseThrow(() -> new ResourceNotFoundException("Virement amende non trouvé: " + virementId));
        if (virement.getStatut() != VirementAmendeStatut.ECHEC) {
            throw new BadRequestException("Seuls les virements en échec peuvent être relancés");
        }
        virement.setStatut(VirementAmendeStatut.EN_ATTENTE);
        virement.setMessageErreur(null);
        virement.setReferenceTransfert(null);
        return virementAmendeRepository.save(virement);
    }

    /** Tx courte 2 : persiste le résultat de l'appel HTTP. */
    @Transactional
    public VirementAmende enregistrerResultat(VirementAmende virement) {
        return virementAmendeRepository.save(virement);
    }

    /**
     * Crée un VirementAmende(EN_ATTENTE) à l'intérieur de la transaction appelante.
     * Doit être appelé depuis le webhook @Transactional avant le commit,
     * puis dispatché via afterCommit → effectuerVirementAsyncParId.
     */
    @Transactional
    public VirementAmende creerVirementEnAttente(Paiement paiement, BigDecimal montantAmende) {
        PaiementMode operateurEffectif = paiement.getModePaiementReel() != null
                ? paiement.getModePaiementReel() : paiement.getOperateur();
        String numeroBenef = resoudreNumeroDeveloppeur(operateurEffectif);

        VirementAmende virement = VirementAmende.builder()
                .paiement(paiement)
                .montant(montantAmende)
                .operateur(operateurEffectif)
                .numeroBeneficiaire(numeroBenef)
                .referenceTontine(paiement.getReferenceTransaction())
                .statut(VirementAmendeStatut.EN_ATTENTE)
                .build();
        return virementAmendeRepository.save(virement);
    }

    /**
     * Exécute le virement Monetbil en arrière-plan, après que la transaction du webhook
     * a commité — garantit qu'aucun fonds ne part si le webhook rollback.
     * L'appel HTTP se fait hors @Transactional pour ne pas retenir de connexion Hikari.
     */
    @Async("notifExecutor")
    public void effectuerVirementAsyncParId(Long virementId) {
        VirementAmende virement = virementAmendeRepository.findById(virementId).orElse(null);
        if (virement == null || virement.getStatut() != VirementAmendeStatut.EN_ATTENTE) return;
        String ref = "AMENDE-" + virement.getReferenceTontine().replace("TONTINE-", "");
        executerTransfert(virement, ref);
        self.enregistrerResultat(virement);
    }

    public Page<VirementAmende> listerVirements(VirementAmendeStatut statut, Pageable pageable) {
        if (statut != null) {
            return virementAmendeRepository.findByStatutOrderByCreatedAtDesc(statut, pageable);
        }
        return virementAmendeRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void executerTransfert(VirementAmende virement, String reference) {
        try {
            MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
            payload.add("service", monetbilServiceKey);
            payload.add("amount",  String.valueOf(virement.getMontant().longValue()));
            payload.add("phone",   virement.getNumeroBeneficiaire());
            payload.add("item_ref", reference);
            payload.add("sign",    signerRequete(payload));

            log.info("[VirementAmende] Transfert {} XAF vers {} ref={}",
                    virement.getMontant(), virement.getNumeroBeneficiaire(), reference);

            JsonNode response = monetbilGateway.callApi(monetbilTransferUrl, payload);
            int success = response.path("success").asInt(0);

            if (success == 1) {
                virement.setStatut(VirementAmendeStatut.SUCCES);
                virement.setReferenceTransfert(response.path("transaction_id").asText(""));
                virement.setDateVirement(LocalDateTime.now());
                log.info("[VirementAmende] SUCCES — {} XAF → {} ({})",
                        virement.getMontant(), virement.getNumeroBeneficiaire(), reference);
            } else {
                String msg = response.path("message").asText("Échec inconnu Monetbil");
                virement.setStatut(VirementAmendeStatut.ECHEC);
                virement.setMessageErreur(msg);
                log.warn("[VirementAmende] ECHEC — {} : {}", reference, msg);
            }

        } catch (Exception e) {
            virement.setStatut(VirementAmendeStatut.ECHEC);
            virement.setMessageErreur(e.getMessage());
            log.error("[VirementAmende] Erreur transfert {} : {}", reference, e.getMessage());
        }
    }

    private String resoudreNumeroDeveloppeur(PaiementMode operateur) {
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
