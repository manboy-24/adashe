package com.tontine.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tontine.dto.request.PaiementMobileMoneyRequest;
import com.tontine.dto.response.*;
import com.tontine.entity.*;
import com.tontine.enums.*;
import com.tontine.exception.*;
import com.tontine.repository.*;
import com.tontine.gateway.MonetbilGateway;
import com.tontine.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
@Transactional
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

    @Value("${monetbil.api-url:https://api.monetbil.com/payment/v1/placePayment}")
    private String monetbilApiUrl;

    @Value("${monetbil.notify-url}")
    private String monetbilNotifyUrl;

    @Value("${monetbil.return-url}")
    private String monetbilReturnUrl;

    // ── Initier un paiement Mobile Money ─────────────────────────────────────

    @Override
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

        // Déterminer l'amende si c'est un rattrapage de cycle
        BigDecimal montantAmende = BigDecimal.ZERO;
        if (request.getNumeroCycle() != null && request.getNumeroCycle() < tontine.getCycleActuel()) {
            montantAmende = tontine.getMontantAmende();
            BigDecimal montantAttendu = tontine.getMontantContribution().add(montantAmende);
            if (request.getMontant().compareTo(montantAttendu) < 0) {
                throw new BadRequestException(
                        "Montant insuffisant pour le rattrapage : " + montantAttendu
                        + " XAF requis (cotisation " + tontine.getMontantContribution()
                        + " + amende " + montantAmende + ")");
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
                .build();
        paiement = paiementRepository.save(paiement);

        // Appeler l'API Monetbil
        try {
            log.info("Initiation paiement: operateur={} numero={} montant={}", request.getOperateur(), request.getNumeroPaiement(), request.getMontant());
            MultiValueMap<String, String> payload = buildMonetbilPayload(request, reference, membre, tontine);
            log.info("Payload Monetbil envoyé: {}", payload);
            JsonNode response = monetbilGateway.callApi(monetbilApiUrl, payload);

            int success = response.path("success").asInt(0);
            if (success == 1) {
                String widgetUrl   = response.path("widget_url").asText("");
                String paymentRef  = response.path("payment_ref").asText("");

                paiement.setGatewayTransactionId(paymentRef);
                paiement.setGatewayPaymentUrl(widgetUrl);
                paiementRepository.save(paiement);

                String instructions = request.getOperateur() == PaiementMode.MTN_MOBILE_MONEY
                        ? "Confirmez le paiement sur votre téléphone MTN MoMo."
                        : "Confirmez le paiement via Orange Money.";

                log.info("Paiement Monetbil initié: ref={} opérateur={}", reference, request.getOperateur());

                return PaiementResponse.builder()
                        .id(paiement.getId())
                        .referenceTransaction(reference)
                        .montant(request.getMontant())
                        .devise("XAF")
                        .operateur(request.getOperateur())
                        .statut(PaiementStatus.EN_ATTENTE)
                        .numeroPaieur(request.getNumeroPaiement())
                        .urlPaiement(widgetUrl.isBlank() ? null : widgetUrl)
                        .messageOperateur("Paiement initié via Monetbil.")
                        .instructions(instructions)
                        .createdAt(paiement.getCreatedAt())
                        .build();
            } else {
                String message = response.path("message").asText("Erreur opérateur");
                paiement.setStatut(PaiementStatus.ANNULE);
                paiement.setMessageOperateur(message);
                paiementRepository.save(paiement);
                throw new BadRequestException("Échec initialisation paiement: " + message);
            }

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur API Monetbil: {}", e.getMessage());
            paiement.setStatut(PaiementStatus.ANNULE);
            paiementRepository.save(paiement);
            throw new BadRequestException("Service de paiement temporairement indisponible");
        }
    }

    // ── Traiter le callback Monetbil ─────────────────────────────────────────

    @Override
    public ApiResponse<String> traiterCallbackMonetbil(Map<String, String> payload) {
        String reference  = payload.get("item_ref");
        String paymentRef = payload.get("payment_ref");
        String statut     = payload.get("status");       // SUCCESS | FAILED | PENDING
        String sign       = payload.get("sign");

        log.info("Callback Monetbil reçu: ref={} statut={}", reference, statut);

        // ── Vérification de signature HMAC-SHA512 ─────────────────────────────
        if (!verifierSignatureMonetbil(payload, sign)) {
            log.warn("Signature Monetbil invalide pour ref={}", reference);
            return ApiResponse.error("Signature invalide");
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

            // Virer l'amende vers le compte développeur si présente
            if (paiement.getMontantAmende() != null
                    && paiement.getMontantAmende().compareTo(BigDecimal.ZERO) > 0) {
                virementAmendeService.effectuerVirement(paiement, paiement.getMontantAmende());
                log.info("Virement amende déclenché: {} XAF — ref={}", paiement.getMontantAmende(), reference);
            }

            notificationService.creerNotification(
                    paiement.getMembre().getUtilisateur(),
                    paiement.getMembre().getTontine(),
                    "✅ Paiement confirmé",
                    "Votre cotisation de " + paiement.getMontant() + " XAF a été confirmée.",
                    com.tontine.enums.NotificationType.PAIEMENT_RECU
            );

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

    @Override
    public List<PaiementResponse> getMesPaiements(Long userId) {
        return paiementRepository.findByUtilisateurIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    /** Construit le payload form-urlencoded pour l'API Monetbil. */
    private MultiValueMap<String, String> buildMonetbilPayload(
            PaiementMobileMoneyRequest request,
            String reference,
            MembreTontine membre,
            Tontine tontine) {

        // Codes opérateur Monetbil Cameroun
        String operator = request.getOperateur() == PaiementMode.MTN_MOBILE_MONEY
                ? "MTN_MOMO_CM" : "ORANGE_CM";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        String phone = request.getNumeroPaiement().replaceAll("\\s+", "");
        if (!phone.startsWith("237")) phone = "237" + phone;

        params.add("service",    monetbilServiceKey);
        params.add("amount",     String.valueOf(request.getMontant().longValue()));
        params.add("phone",      phone);
        params.add("msisdn",     phone);
        params.add("operator",   operator);
        params.add("locale",     "fr");
        params.add("item_ref",   reference);
        params.add("user1",      "tontine_" + tontine.getId());
        params.add("user2",      "membre_"  + membre.getId());
        params.add("notify_url", monetbilNotifyUrl);
        params.add("return_url", monetbilReturnUrl);

        return params;
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

        // Vérifier si pas déjà enregistré ce cycle
        boolean existe = cotisationRepository
                .findByMembreIdAndTontineIdAndNumeroCycle(membre.getId(), tontine.getId(), tontine.getCycleActuel())
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
                    .numeroCycle(tontine.getCycleActuel())
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
                .createdAt(p.getCreatedAt())
                .build();
    }
}
