package com.tontine.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tontine.dto.request.ConfirmerPaiementMonetbilRequest;
import com.tontine.dto.request.PaiementEspecesRequest;
import com.tontine.dto.request.PaiementMobileMoneyRequest;
import com.tontine.dto.response.*;
import com.tontine.entity.*;
import com.tontine.enums.*;
import com.tontine.exception.*;
import com.tontine.enums.MembreStatut;
import com.tontine.repository.*;
import com.tontine.gateway.MonetbilGateway;
import com.tontine.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.URLEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaiementServiceImpl implements PaiementService {

    private final PaiementRepository paiementRepository;
    private final MembreTontineRepository membreRepository;
    private final CotisationRepository cotisationRepository;
    private final TontineRepository tontineRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;
    private final MonetbilGateway monetbilGateway;
    private final ObjectMapper objectMapper;
    private final VirementAmendeService virementAmendeService;

    @Value("${monetbil.service-key}")
    private String monetbilServiceKey;

    @Value("${monetbil.service-secret}")
    private String monetbilServiceSecret;

    // URL construite dynamiquement : https://www.monetbil.com/widget/v2.1/{service_key}
    private String monetbilWidgetUrl() {
        return "https://www.monetbil.com/widget/v2.1/" + monetbilServiceKey;
    }

    @Value("${monetbil.check-payment-url:https://api.monetbil.com/payment/v1/checkPayment}")
    private String monetbilCheckPaymentUrl;

    private static final String MONETBIL_PLACE_PAYMENT_URL = "https://api.monetbil.com/payment/v1/placePayment";

    @Value("${monetbil.notify-url}")
    private String monetbilNotifyUrl;

    @Value("${monetbil.return-url}")
    private String monetbilReturnUrl;

    // ── Initier un paiement Mobile Money ─────────────────────────────────────

    @Override
    @Transactional
    public PaiementResponse initierPaiement(PaiementMobileMoneyRequest request, Long userId) {
        // Vérifications
        MembreTontine membre = membreRepository.findById(request.getMembreId())
                .orElseThrow(() -> new ResourceNotFoundException("Membre non trouvé"));

        Tontine tontine = tontineRepository.findById(request.getTontineId())
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));

        // Vérifier que l'utilisateur est bien le membre concerné
        if (!membre.getUtilisateur().getId().equals(userId)) {
            throw new ForbiddenException("Vous ne pouvez payer que pour votre propre compte");
        }

        // Valider l'opérateur pour le Cameroun
        if (request.getOperateur() != PaiementMode.MTN_MOBILE_MONEY
                && request.getOperateur() != PaiementMode.ORANGE_MONEY) {
            throw new BadRequestException("Opérateur non supporté. Choisissez MTN_MOBILE_MONEY ou ORANGE_MONEY");
        }

        // Générer la référence unique
        String reference = "TONTINE-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        // Rejeter un numeroCycle dans le futur (anti-double-débit, bug_007)
        if (request.getNumeroCycle() != null && request.getNumeroCycle() > tontine.getCycleActuel()) {
            throw new BadRequestException("Cycle invalide : maximum " + tontine.getCycleActuel());
        }

        // Rejeter si cotisation déjà payée pour ce cycle (anti-doublon)
        int targetCycle = (request.getNumeroCycle() != null && request.getNumeroCycle() >= 1)
                ? request.getNumeroCycle() : tontine.getCycleActuel();
        boolean dejaPaye = cotisationRepository
                .findByMembreIdAndTontineIdAndNumeroCycle(membre.getId(), tontine.getId(), targetCycle)
                .filter(c -> c.getStatut() == PaiementStatus.PAYE)
                .isPresent();
        if (dejaPaye) {
            throw new BadRequestException("La cotisation pour ce cycle est déjà enregistrée");
        }

        // Vérifier l'ordre des cycles : le membre doit payer dans l'ordre chronologique
        if (targetCycle > 1) {
            Set<Integer> cyclesTrackes = cotisationRepository.findCyclesTrackesAvant(tontine.getId(), targetCycle);
            Set<Integer> cyclesPayes  = cotisationRepository.findCyclesPayesParMembre(membre.getId(), tontine.getId(), targetCycle);
            cyclesTrackes.stream()
                    .filter(c -> !cyclesPayes.contains(c))
                    .min(Integer::compareTo)
                    .ifPresent(premierImpaye -> {
                        throw new BadRequestException(
                            "Cycle " + premierImpaye + " non payé — réglez d'abord le cycle " + premierImpaye + " avant le cycle " + targetCycle);
                    });
        }

        // Auto-annuler les paiements EN_ATTENTE expirés (>30 min) pour débloquer le membre
        paiementRepository.annulerPaiementsExpiresParMembre(
                membre.getId(), PaiementStatus.EN_ATTENTE, LocalDateTime.now().minusMinutes(10));

        // Rejeter si un paiement EN_ATTENTE récent existe encore (anti-concurrent)
        if (paiementRepository.existsByMembreIdAndStatut(membre.getId(), PaiementStatus.EN_ATTENTE)) {
            throw new BadRequestException("Un paiement est déjà en cours pour ce membre. Veuillez patienter.");
        }

        // Valider le montant contre la cotisation attendue en base
        BigDecimal montantAmende = BigDecimal.ZERO;
        if (request.getNumeroCycle() != null
                && request.getNumeroCycle() >= 1
                && request.getNumeroCycle() < tontine.getCycleActuel()) {
            // Rattrapage de cycle : cotisation + amende obligatoire
            montantAmende = tontine.getMontantAmende();
            BigDecimal montantAttendu = tontine.getMontantContribution().add(montantAmende);
            if (request.getMontant().compareTo(montantAttendu) < 0) {
                throw new BadRequestException(
                        "Montant insuffisant pour le rattrapage : " + montantAttendu
                        + " XAF requis (cotisation " + tontine.getMontantContribution()
                        + " + amende " + montantAmende + ")");
            }
        } else {
            // Cycle courant : le montant doit couvrir au minimum la cotisation définie
            BigDecimal montantAttendu = tontine.getMontantContribution();
            if (request.getMontant().compareTo(montantAttendu) < 0) {
                throw new BadRequestException(
                        "Montant insuffisant : " + montantAttendu
                        + " XAF requis pour la cotisation");
            }
        }

        // Créer le paiement en base
        Paiement paiement = Paiement.builder()
                .membre(membre)
                .montant(request.getMontant())
                .montantAmende(montantAmende)
                .devise("XAF")
                .operateur(request.getOperateur())
                .statut(PaiementStatus.EN_ATTENTE)
                .referenceTransaction(reference)
                .numeroPaieur(request.getNumeroPaiement())
                .numeroCycle(targetCycle)
                .build();
        paiement = paiementRepository.save(paiement);

        String phone = buildPhone(request.getNumeroPaiement());

        if (request.getOperateur() == PaiementMode.MTN_MOBILE_MONEY) {
            // ── MTN : push automatique via api.monetbil.com/placePayment ─────
            MultiValueMap<String, String> mtnParams = new LinkedMultiValueMap<>();
            mtnParams.add("service",     monetbilServiceKey);
            mtnParams.add("amount",      String.valueOf(request.getMontant().longValue()));
            mtnParams.add("phonenumber", phone);
            mtnParams.add("item_ref",    reference);
            mtnParams.add("notify_url",  monetbilNotifyUrl);

            try {
                JsonNode mtnResp = monetbilGateway.callApi(MONETBIL_PLACE_PAYMENT_URL, mtnParams);
                String mtnStatus = mtnResp.path("status").asText("");
                String paymentId = mtnResp.path("paymentId").asText(reference);

                if ("REQUEST_ACCEPTED".equals(mtnStatus)) {
                    paiement.setGatewayTransactionId(paymentId);
                    paiementRepository.save(paiement);
                    log.info("Paiement MTN push initié via Monetbil: ref={} paymentId={}", reference, paymentId);
                    return PaiementResponse.builder()
                            .id(paiement.getId())
                            .referenceTransaction(reference)
                            .montant(request.getMontant())
                            .devise("XAF")
                            .operateur(PaiementMode.MTN_MOBILE_MONEY)
                            .statut(PaiementStatus.EN_ATTENTE)
                            .numeroPaieur(request.getNumeroPaiement())
                            .messageOperateur("Demande envoyée.")
                            .instructions("Vérifiez votre téléphone MTN MoMo et entrez votre PIN pour confirmer.")
                            .createdAt(paiement.getCreatedAt())
                            .build();
                } else {
                    log.warn("Monetbil placePayment MTN statut inattendu: {}", mtnStatus);
                }
            } catch (Exception e) {
                log.error("Erreur Monetbil placePayment MTN: {}", e.getMessage());
            }

            // Fallback widget URL si placePayment échoue
            String widgetUrl = buildWidgetGetUrl(request.getMontant(), phone, reference,
                    "tontine_" + tontine.getId(), "membre_" + membre.getId());
            paiement.setGatewayPaymentUrl(widgetUrl);
            paiementRepository.save(paiement);
            return PaiementResponse.builder()
                    .id(paiement.getId())
                    .referenceTransaction(reference)
                    .montant(request.getMontant())
                    .devise("XAF")
                    .operateur(PaiementMode.MTN_MOBILE_MONEY)
                    .statut(PaiementStatus.EN_ATTENTE)
                    .numeroPaieur(request.getNumeroPaiement())
                    .urlPaiement(widgetUrl)
                    .instructions("Confirmez le paiement sur votre téléphone MTN MoMo.")
                    .createdAt(paiement.getCreatedAt())
                    .build();
        } else {
            // ── Orange Money : push automatique via api.monetbil.com/placePayment ──
            MultiValueMap<String, String> orangeParams = new LinkedMultiValueMap<>();
            orangeParams.add("service",     monetbilServiceKey);
            orangeParams.add("amount",      String.valueOf(request.getMontant().longValue()));
            orangeParams.add("phonenumber", phone);
            orangeParams.add("item_ref",    reference);
            orangeParams.add("notify_url",  monetbilNotifyUrl);

            try {
                JsonNode orangeResp = monetbilGateway.callApi(MONETBIL_PLACE_PAYMENT_URL, orangeParams);
                String orangeStatus = orangeResp.path("status").asText("");
                String paymentId    = orangeResp.path("paymentId").asText(reference);

                if ("REQUEST_ACCEPTED".equals(orangeStatus)) {
                    paiement.setGatewayTransactionId(paymentId);
                    paiementRepository.save(paiement);
                    log.info("Paiement Orange push initié via Monetbil: ref={} paymentId={}", reference, paymentId);
                    return PaiementResponse.builder()
                            .id(paiement.getId())
                            .referenceTransaction(reference)
                            .montant(request.getMontant())
                            .devise("XAF")
                            .operateur(PaiementMode.ORANGE_MONEY)
                            .statut(PaiementStatus.EN_ATTENTE)
                            .numeroPaieur(request.getNumeroPaiement())
                            .messageOperateur("Demande envoyée.")
                            .instructions("Vérifiez votre téléphone Orange Money et entrez votre PIN pour confirmer.")
                            .createdAt(paiement.getCreatedAt())
                            .build();
                } else {
                    log.warn("Monetbil placePayment Orange statut inattendu: {}", orangeStatus);
                }
            } catch (Exception e) {
                log.error("Erreur Monetbil placePayment Orange: {}", e.getMessage());
            }

            // Fallback widget URL si placePayment échoue
            String widgetUrl = buildWidgetGetUrl(request.getMontant(), phone, reference,
                    "tontine_" + tontine.getId(), "membre_" + membre.getId());
            paiement.setGatewayPaymentUrl(widgetUrl);
            paiementRepository.save(paiement);
            log.info("Paiement Orange fallback widget URL: ref={}", reference);
            return PaiementResponse.builder()
                    .id(paiement.getId())
                    .referenceTransaction(reference)
                    .montant(request.getMontant())
                    .devise("XAF")
                    .operateur(PaiementMode.ORANGE_MONEY)
                    .statut(PaiementStatus.EN_ATTENTE)
                    .numeroPaieur(request.getNumeroPaiement())
                    .urlPaiement(widgetUrl)
                    .instructions("Confirmez le paiement via Orange Money.")
                    .createdAt(paiement.getCreatedAt())
                    .build();
        }
    }

    // ── Paiement espèces : admin paie en MoMo pour un membre ─────────────────

    @Override
    @Transactional
    public PaiementResponse initierPaiementEspeces(PaiementEspecesRequest request, Long adminId) {

        Tontine tontine = tontineRepository.findById(request.getTontineId())
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));

        // Seul le créateur de la tontine peut effectuer ce paiement
        if (!tontine.getCreateur().getId().equals(adminId)) {
            throw new ForbiddenException("Seul le créateur de la tontine peut payer pour un membre");
        }

        MembreTontine membre = membreRepository.findById(request.getMembreId())
                .orElseThrow(() -> new ResourceNotFoundException("Membre non trouvé"));

        if (!membre.getTontine().getId().equals(tontine.getId())) {
            throw new BadRequestException("Ce membre n'appartient pas à cette tontine");
        }

        // Vérifier que le membre est actif dans la tontine (bug_028)
        if (!Boolean.TRUE.equals(membre.getActif()) || membre.getStatutMembre() != MembreStatut.ACTIF) {
            throw new BadRequestException("Ce membre n'est pas actif dans la tontine");
        }

        if (request.getOperateurReel() != PaiementMode.MTN_MOBILE_MONEY
                && request.getOperateurReel() != PaiementMode.ORANGE_MONEY) {
            throw new BadRequestException("Opérateur invalide. Choisissez MTN_MOBILE_MONEY ou ORANGE_MONEY");
        }

        // Rejeter un numeroCycle dans le futur (bug_007)
        if (request.getNumeroCycle() != null && request.getNumeroCycle() > tontine.getCycleActuel()) {
            throw new BadRequestException("Cycle invalide : maximum " + tontine.getCycleActuel());
        }

        int targetCycle = (request.getNumeroCycle() != null && request.getNumeroCycle() >= 1)
                ? request.getNumeroCycle() : tontine.getCycleActuel();

        boolean dejaPaye = cotisationRepository
                .findByMembreIdAndTontineIdAndNumeroCycle(membre.getId(), tontine.getId(), targetCycle)
                .filter(c -> c.getStatut() == PaiementStatus.PAYE)
                .isPresent();
        if (dejaPaye) {
            throw new BadRequestException("La cotisation pour ce cycle est déjà enregistrée");
        }

        // Auto-annuler les paiements EN_ATTENTE expirés (>30 min) pour débloquer le membre
        paiementRepository.annulerPaiementsExpiresParMembre(
                membre.getId(), PaiementStatus.EN_ATTENTE, LocalDateTime.now().minusMinutes(10));

        if (paiementRepository.existsByMembreIdAndStatut(membre.getId(), PaiementStatus.EN_ATTENTE)) {
            throw new BadRequestException("Un paiement est déjà en cours pour ce membre. Veuillez patienter.");
        }

        // Valider le montant contre la cotisation attendue (bug_015)
        BigDecimal montantAmende = BigDecimal.ZERO;
        if (request.getNumeroCycle() != null
                && request.getNumeroCycle() >= 1
                && request.getNumeroCycle() < tontine.getCycleActuel()) {
            montantAmende = tontine.getMontantAmende();
            BigDecimal montantAttendu = tontine.getMontantContribution().add(montantAmende);
            if (request.getMontant().compareTo(montantAttendu) < 0) {
                throw new BadRequestException(
                        "Montant insuffisant pour le rattrapage : " + montantAttendu
                        + " XAF requis (cotisation " + tontine.getMontantContribution()
                        + " + amende " + montantAmende + ")");
            }
        } else {
            if (request.getMontant().compareTo(tontine.getMontantContribution()) < 0) {
                throw new BadRequestException(
                        "Montant insuffisant : " + tontine.getMontantContribution()
                        + " XAF requis pour la cotisation");
            }
        }

        Utilisateur admin = utilisateurRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Administrateur non trouvé"));

        String reference = "TONTINE-ESP-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        Paiement paiement = Paiement.builder()
                .membre(membre)
                .montant(request.getMontant())
                .montantAmende(montantAmende)
                .devise("XAF")
                .operateur(PaiementMode.ESPECES)
                .modePaiementReel(request.getOperateurReel())
                .statut(PaiementStatus.EN_ATTENTE)
                .referenceTransaction(reference)
                .numeroPaieur(request.getNumeroPaiement())
                .payePourCompte(true)
                .adminPayeur(admin)
                .numeroCycle(targetCycle)
                .build();
        paiement = paiementRepository.save(paiement);

        String espPhone = buildPhone(request.getNumeroPaiement());

        // ── Push automatique via api.monetbil.com/placePayment (MTN + Orange) ──
        MultiValueMap<String, String> pushParams = new LinkedMultiValueMap<>();
        pushParams.add("service",     monetbilServiceKey);
        pushParams.add("amount",      String.valueOf(request.getMontant().longValue()));
        pushParams.add("phonenumber", espPhone);
        pushParams.add("item_ref",    reference);
        pushParams.add("notify_url",  monetbilNotifyUrl);

        try {
            JsonNode pushResp = monetbilGateway.callApi(MONETBIL_PLACE_PAYMENT_URL, pushParams);
            String pushStatus = pushResp.path("status").asText("");
            String paymentId  = pushResp.path("paymentId").asText(reference);

            if ("REQUEST_ACCEPTED".equals(pushStatus)) {
                paiement.setGatewayTransactionId(paymentId);
                paiementRepository.save(paiement);
                String operateur = request.getOperateurReel() == PaiementMode.MTN_MOBILE_MONEY ? "MTN MoMo" : "Orange Money";
                log.info("Paiement espèces {} push initié: ref={} paymentId={}", operateur, reference, paymentId);
                return PaiementResponse.builder()
                        .id(paiement.getId())
                        .referenceTransaction(reference)
                        .montant(request.getMontant())
                        .devise("XAF")
                        .operateur(PaiementMode.ESPECES)
                        .statut(PaiementStatus.EN_ATTENTE)
                        .numeroPaieur(request.getNumeroPaiement())
                        .instructions("Vérifiez votre téléphone " + operateur + " et entrez votre PIN pour confirmer.")
                        .payePourCompte(true)
                        .createdAt(paiement.getCreatedAt())
                        .build();
            } else {
                log.warn("Monetbil placePayment espèces statut inattendu: {}", pushStatus);
            }
        } catch (Exception e) {
            log.error("Erreur Monetbil placePayment espèces: {}", e.getMessage());
        }

        // Fallback widget URL
        String espWidgetUrl = buildWidgetGetUrl(request.getMontant(), espPhone, reference,
                "tontine_" + tontine.getId(), "membre_" + membre.getId());
        paiement.setGatewayPaymentUrl(espWidgetUrl);
        paiementRepository.save(paiement);

        String instructions = request.getOperateurReel() == PaiementMode.MTN_MOBILE_MONEY
                ? "Confirmez le paiement sur votre téléphone MTN MoMo."
                : "Confirmez le paiement via Orange Money.";

        log.info("Paiement espèces initié (widget URL): ref={}", reference);

        return PaiementResponse.builder()
                .id(paiement.getId())
                .referenceTransaction(reference)
                .montant(request.getMontant())
                .devise("XAF")
                .operateur(PaiementMode.ESPECES)
                .statut(PaiementStatus.EN_ATTENTE)
                .numeroPaieur(request.getNumeroPaiement())
                .urlPaiement(espWidgetUrl)
                .instructions(instructions)
                .payePourCompte(true)
                .createdAt(paiement.getCreatedAt())
                .build();
    }

    // ── Traiter le callback Monetbil ─────────────────────────────────────────

    @Override
    @Transactional
    public ApiResponse<String> traiterCallbackMonetbil(Map<String, String> payload) {
        String reference  = payload.get("item_ref");
        String paymentRef = payload.get("payment_ref");
        String statut     = payload.get("status");       // SUCCESS | FAILED | PENDING
        String sign       = payload.get("sign");

        log.info("Callback Monetbil reçu: ref={} statut={}", reference, statut);

        // ── Vérification de signature HMAC-SHA512 ─────────────────────────────
        if (!verifierSignatureMonetbil(payload, sign)) {
            log.warn("Signature Monetbil invalide pour ref={}", reference);
            throw new ForbiddenException("Signature invalide");
        }

        // Verrou pessimiste : évite le double traitement en cas de callbacks simultanés
        Paiement paiement = paiementRepository.findByReferenceTransactionForUpdate(reference)
                .orElseGet(() -> paiementRepository.findByGatewayTransactionIdForUpdate(paymentRef).orElse(null));

        if (paiement == null) {
            log.warn("Paiement non trouvé pour ref={}", reference);
            return ApiResponse.error("Paiement non trouvé");
        }

        // Idempotence : si déjà traité, on acquitte sans retraiter
        if (paiement.getStatut() == PaiementStatus.PAYE
                || paiement.getStatut() == PaiementStatus.ANNULE) {
            return ApiResponse.success(null, "Déjà traité");
        }

        if ("SUCCESS".equalsIgnoreCase(statut)) {
            // ✅ Paiement réussi
            paiement.setStatut(PaiementStatus.PAYE);
            paiement.setDatePaiement(LocalDateTime.now());
            paiement.setGatewayTransactionId(paymentRef);
            paiement.setMessageOperateur("Confirmé via Monetbil (" + payload.getOrDefault("operator", "Mobile Money") + ")");
            paiementRepository.save(paiement);

            enregistrerCotisationApresPaiement(paiement);

            // Persister le VirementAmende dans cette transaction puis le déclencher
            // après le commit — garantit qu'aucun argent ne part si le webhook rollback
            if (paiement.getMontantAmende() != null
                    && paiement.getMontantAmende().compareTo(BigDecimal.ZERO) > 0) {
                VirementAmende virement = virementAmendeService.creerVirementEnAttente(
                        paiement, paiement.getMontantAmende());
                Long virementId = virement.getId();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        virementAmendeService.effectuerVirementAsyncParId(virementId);
                    }
                });
                log.info("Virement amende planifié après commit: {} XAF — ref={}", paiement.getMontantAmende(), reference);
            }

            if (Boolean.TRUE.equals(paiement.getPayePourCompte())) {
                // Paiement espèces : notifier le membre
                notificationService.creerNotification(
                        paiement.getMembre().getUtilisateur(),
                        paiement.getMembre().getTontine(),
                        "💵 Cotisation enregistrée",
                        "Votre cotisation de " + paiement.getMontant() + " XAF a été enregistrée en espèces par l'administrateur.",
                        com.tontine.enums.NotificationType.PAIEMENT_RECU
                );
                // Notifier l'admin
                if (paiement.getAdminPayeur() != null) {
                    notificationService.creerNotification(
                            paiement.getAdminPayeur(),
                            paiement.getMembre().getTontine(),
                            "✅ Paiement espèces confirmé",
                            "Paiement de " + paiement.getMontant() + " XAF confirmé pour "
                                    + paiement.getMembre().getUtilisateur().getNom() + ".",
                            com.tontine.enums.NotificationType.PAIEMENT_RECU
                    );
                }
            } else {
                // Notifier le membre : sa cotisation est confirmée
                notificationService.creerNotification(
                        paiement.getMembre().getUtilisateur(),
                        paiement.getMembre().getTontine(),
                        "✅ Paiement confirmé",
                        "Votre cotisation de " + paiement.getMontant() + " XAF a été confirmée.",
                        com.tontine.enums.NotificationType.PAIEMENT_RECU
                );
                // Notifier le créateur de la tontine (si différent du membre)
                Utilisateur createur = paiement.getMembre().getTontine().getCreateur();
                if (createur != null && !createur.getId().equals(paiement.getMembre().getUtilisateur().getId())) {
                    notificationService.creerNotification(
                            createur,
                            paiement.getMembre().getTontine(),
                            "💰 Cotisation reçue",
                            paiement.getMembre().getUtilisateur().getPrenom() + " "
                                    + paiement.getMembre().getUtilisateur().getNom()
                                    + " a payé " + paiement.getMontant() + " XAF via Mobile Money.",
                            com.tontine.enums.NotificationType.PAIEMENT_RECU
                    );
                }
            }

            log.info("Cotisation enregistrée après paiement Monetbil: ref={}", reference);
            return ApiResponse.success(null, "Paiement confirmé");

        } else if ("FAILED".equalsIgnoreCase(statut)) {
            // ❌ Paiement échoué
            paiement.setStatut(PaiementStatus.ANNULE);
            paiement.setMessageOperateur("Échec Monetbil: " + payload.getOrDefault("message", "inconnu"));
            paiementRepository.save(paiement);

            notificationService.creerNotification(
                    paiement.getMembre().getUtilisateur(),
                    paiement.getMembre().getTontine(),
                    "❌ Échec du paiement",
                    "Le paiement de " + paiement.getMontant() + " XAF a échoué. Veuillez réessayer.",
                    com.tontine.enums.NotificationType.RETARD_PAIEMENT
            );
            return ApiResponse.error("Paiement échoué");

        } else {
            // PENDING — rien à faire, on attend le callback final
            log.info("Paiement en attente Monetbil: ref={}", reference);
            return ApiResponse.success(null, "En attente");
        }
    }

    // ── Confirmation SDK Android (après onPaymentSuccess) ────────────────────

    @Override
    @Transactional
    public PaiementResponse confirmerPaiementMonetbil(ConfirmerPaiementMonetbilRequest request, Long userId) {
        // Verrou pessimiste — évite la concurrence avec le webhook Monetbil
        Paiement paiement = paiementRepository.findByReferenceTransactionForUpdate(request.getItemRef())
                .orElseThrow(() -> new ResourceNotFoundException("Paiement non trouvé : " + request.getItemRef()));

        if (!paiement.getMembre().getUtilisateur().getId().equals(userId)) {
            throw new ForbiddenException("Vous ne pouvez confirmer que votre propre paiement");
        }

        // Idempotence : webhook a peut-être déjà traité ce paiement
        if (paiement.getStatut() == PaiementStatus.PAYE) {
            return toResponse(paiement);
        }
        if (paiement.getStatut() == PaiementStatus.ANNULE) {
            throw new BadRequestException("Ce paiement a été annulé");
        }

        // Vérifier auprès de Monetbil
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("paymentId", request.getTransactionUuid());
            JsonNode result = monetbilGateway.callApi(monetbilCheckPaymentUrl, params);

            int status = result.path("status").asInt(0);
            // 1 = succès live, 7 = succès test
            if (status == 1 || status == 7) {
                paiement.setStatut(PaiementStatus.PAYE);
                paiement.setDatePaiement(LocalDateTime.now());
                paiement.setGatewayTransactionId(request.getTransactionUuid());
                paiement.setMessageOperateur("Confirmé via SDK Android Monetbil");
                paiementRepository.save(paiement);

                enregistrerCotisationApresPaiement(paiement);

                if (paiement.getMontantAmende() != null
                        && paiement.getMontantAmende().compareTo(BigDecimal.ZERO) > 0) {
                    VirementAmende virement = virementAmendeService.creerVirementEnAttente(
                            paiement, paiement.getMontantAmende());
                    Long virementId = virement.getId();
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            virementAmendeService.effectuerVirementAsyncParId(virementId);
                        }
                    });
                }

                notificationService.creerNotification(
                        paiement.getMembre().getUtilisateur(),
                        paiement.getMembre().getTontine(),
                        "✅ Paiement confirmé",
                        "Votre cotisation de " + paiement.getMontant() + " XAF a été confirmée.",
                        com.tontine.enums.NotificationType.PAIEMENT_RECU
                );

                Utilisateur createur = paiement.getMembre().getTontine().getCreateur();
                if (createur != null && !createur.getId().equals(paiement.getMembre().getUtilisateur().getId())) {
                    notificationService.creerNotification(
                            createur,
                            paiement.getMembre().getTontine(),
                            "💰 Cotisation reçue",
                            paiement.getMembre().getUtilisateur().getPrenom() + " "
                                    + paiement.getMembre().getUtilisateur().getNom()
                                    + " a payé " + paiement.getMontant() + " XAF via Mobile Money.",
                            com.tontine.enums.NotificationType.PAIEMENT_RECU
                    );
                }

                log.info("Paiement confirmé via SDK Android: ref={}", request.getItemRef());
                return toResponse(paiement);

            } else {
                // -1 / 0 / 8 / 9 = annulé ou échoué
                paiement.setStatut(PaiementStatus.ANNULE);
                paiement.setMessageOperateur("Paiement non abouti (statut Monetbil: " + status + ")");
                paiementRepository.save(paiement);
                throw new BadRequestException("Paiement non confirmé par Monetbil (statut: " + status + ")");
            }

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur checkPayment Monetbil: {}", e.getMessage());
            throw new BadRequestException("Impossible de vérifier le paiement auprès de Monetbil");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaiementResponse> getMesPaiements(Long userId) {
        return paiementRepository.findByUtilisateurIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private String buildPhone(String numeroPaiement) {
        String phone = numeroPaiement.replaceAll("[^0-9]", ""); // supprime +, espaces, tirets
        if (phone.startsWith("00237")) phone = phone.substring(5);
        else if (phone.startsWith("237") && phone.length() > 9) phone = phone.substring(3);
        return "237" + phone;
    }

    private String buildWidgetGetUrl(BigDecimal montant, String phone, String reference,
                                     String user1, String user2) {
        return monetbilWidgetUrl()
                + "?amount="      + montant.longValue()
                + "&phone="       + phone
                + "&locale=fr"
                + "&item_ref="    + URLEncoder.encode(reference, StandardCharsets.UTF_8)
                + "&payment_ref=" + URLEncoder.encode(reference, StandardCharsets.UTF_8)
                + "&user1="       + URLEncoder.encode(user1, StandardCharsets.UTF_8)
                + "&user2="       + URLEncoder.encode(user2, StandardCharsets.UTF_8)
                + "&notify_url="  + URLEncoder.encode(monetbilNotifyUrl, StandardCharsets.UTF_8)
                + "&return_url="  + URLEncoder.encode(monetbilReturnUrl, StandardCharsets.UTF_8);
    }

    /**
     * Vérifie la signature Monetbil.
     * Algorithme : SHA-512 du serviceSecret suivi des valeurs de tous les params
     * (sauf "sign") triés par clé alphabétiquement.
     */
    private boolean verifierSignatureMonetbil(Map<String, String> payload, String sign) {
        if (sign == null || sign.isBlank()) return false;
        try {
            StringBuilder sb = new StringBuilder(monetbilServiceSecret);
            payload.entrySet().stream()
                    .filter(e -> !"sign".equals(e.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append(e.getValue()));

            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(
                    monetbilServiceSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            // Comparaison en temps constant pour éviter les timing attacks
            return MessageDigest.isEqual(
                hex.toString().toLowerCase().getBytes(StandardCharsets.UTF_8),
                sign.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Erreur vérification signature Monetbil: {}", e.getMessage());
            return false;
        }
    }

    private void enregistrerCotisationApresPaiement(Paiement paiement) {
        MembreTontine membre = paiement.getMembre();
        Tontine tontine = membre.getTontine();

        // Utiliser le cycle persisté sur le paiement — fallback sur cycleActuel pour les anciens paiements
        int numeroCycle = paiement.getNumeroCycle() != null
                ? paiement.getNumeroCycle() : tontine.getCycleActuel();

        boolean existe = cotisationRepository
                .findByMembreIdAndTontineIdAndNumeroCycle(membre.getId(), tontine.getId(), numeroCycle)
                .filter(c -> c.getStatut() == PaiementStatus.PAYE)
                .isPresent();

        if (!existe) {
            BigDecimal amende = paiement.getMontantAmende() != null
                    ? paiement.getMontantAmende() : BigDecimal.ZERO;
            boolean estRattrapage = amende.compareTo(BigDecimal.ZERO) > 0;

            Cotisation cotisation = Cotisation.builder()
                    .tontine(tontine)
                    .membre(membre)
                    .montant(paiement.getMontant())
                    .montantAmende(amende)
                    .numeroCycle(numeroCycle)
                    .statut(PaiementStatus.PAYE)
                    .estEnRetard(estRattrapage)
                    .datePaiement(java.time.LocalDate.now())
                    .referenceTransaction(paiement.getReferenceTransaction())
                    .modePaiement(paiement.getOperateur().name())
                    .build();
            cotisationRepository.save(cotisation);
            paiement.setCotisation(cotisation);
            paiementRepository.save(paiement);
        }
    }

    // ── Webhook MTN MADAPI ────────────────────────────────────────────────────

    @Override
    @Transactional
    public ApiResponse<String> traiterCallbackMtn(Map<String, Object> payload) {
        log.info("Callback MTN reçu: {}", payload);

        // MTN envoie : transactionId, externalTransactionId, status, amount…
        String reference     = String.valueOf(payload.getOrDefault("externalTransactionId", ""));
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", ""));
        String statut        = String.valueOf(payload.getOrDefault("status", ""));

        if (reference.isBlank() && transactionId.isBlank()) {
            log.warn("[MTN] Callback sans référence");
            return ApiResponse.error("Référence manquante");
        }

        Paiement paiement = paiementRepository.findByReferenceTransactionForUpdate(reference)
                .orElseGet(() -> paiementRepository.findByGatewayTransactionIdForUpdate(transactionId).orElse(null));

        if (paiement == null) {
            log.warn("[MTN] Paiement non trouvé: ref={}", reference);
            return ApiResponse.error("Paiement non trouvé");
        }

        if (paiement.getStatut() == PaiementStatus.PAYE || paiement.getStatut() == PaiementStatus.ANNULE) {
            return ApiResponse.success(null, "Déjà traité");
        }

        if ("SUCCESSFUL".equalsIgnoreCase(statut) || "COMPLETED".equalsIgnoreCase(statut) || "SUCCESS".equalsIgnoreCase(statut)) {
            paiement.setStatut(PaiementStatus.PAYE);
            paiement.setDatePaiement(LocalDateTime.now());
            paiement.setGatewayTransactionId(transactionId.isBlank() ? reference : transactionId);
            paiement.setMessageOperateur("Confirmé via MTN MoMo");
            paiementRepository.save(paiement);

            enregistrerCotisationApresPaiement(paiement);

            if (paiement.getMontantAmende() != null && paiement.getMontantAmende().compareTo(BigDecimal.ZERO) > 0) {
                VirementAmende virement = virementAmendeService.creerVirementEnAttente(paiement, paiement.getMontantAmende());
                Long virementId = virement.getId();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { virementAmendeService.effectuerVirementAsyncParId(virementId); }
                });
            }

            notificationService.creerNotification(
                    paiement.getMembre().getUtilisateur(), paiement.getMembre().getTontine(),
                    "✅ Paiement confirmé",
                    "Votre cotisation de " + paiement.getMontant() + " XAF a été confirmée via MTN MoMo.",
                    com.tontine.enums.NotificationType.PAIEMENT_RECU);

            Utilisateur createur = paiement.getMembre().getTontine().getCreateur();
            if (createur != null && !createur.getId().equals(paiement.getMembre().getUtilisateur().getId())) {
                notificationService.creerNotification(
                        createur, paiement.getMembre().getTontine(),
                        "💰 Cotisation reçue",
                        paiement.getMembre().getUtilisateur().getPrenom() + " "
                                + paiement.getMembre().getUtilisateur().getNom()
                                + " a payé " + paiement.getMontant() + " XAF via MTN MoMo.",
                        com.tontine.enums.NotificationType.PAIEMENT_RECU);
            }

            log.info("[MTN] Cotisation enregistrée: ref={}", reference);
            return ApiResponse.success(null, "Paiement confirmé");

        } else if ("FAILED".equalsIgnoreCase(statut) || "FAILURE".equalsIgnoreCase(statut)) {
            paiement.setStatut(PaiementStatus.ANNULE);
            paiement.setMessageOperateur("Échec MTN: " + payload.getOrDefault("statusDescription", "inconnu"));
            paiementRepository.save(paiement);
            return ApiResponse.error("Paiement échoué");
        }

        return ApiResponse.success(null, "En attente");
    }

    private PaiementResponse toResponse(Paiement p) {
        return PaiementResponse.builder()
                .id(p.getId())
                .referenceTransaction(p.getReferenceTransaction())
                .montant(p.getMontant())
                .devise(p.getDevise())
                .operateur(p.getOperateur())
                .statut(p.getStatut())
                .numeroPaieur(p.getNumeroPaieur())
                .messageOperateur(p.getMessageOperateur())
                .urlPaiement(p.getGatewayPaymentUrl())
                .payePourCompte(p.getPayePourCompte())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
