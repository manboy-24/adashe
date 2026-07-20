package com.tontine.service.impl;

import com.tontine.dto.request.DonRequest;
import com.tontine.dto.response.ApiResponse;
import com.tontine.dto.response.DonResponse;
import com.tontine.dto.response.DonStatutResponse;
import com.tontine.entity.Don;
import com.tontine.entity.Tirage;
import com.tontine.entity.Utilisateur;
import com.tontine.enums.NotificationType;
import com.tontine.enums.PaiementMode;
import com.tontine.enums.PaiementStatus;
import com.tontine.exception.BadRequestException;
import com.tontine.gateway.MonetbilGateway;
import com.tontine.repository.DonRepository;
import com.tontine.repository.TirageRepository;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.service.DonService;
import com.tontine.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DonServiceImpl implements DonService {

    private final DonRepository         donRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final TirageRepository      tirageRepository;
    private final NotificationService   notificationService;
    private final MonetbilGateway       monetbilGateway;

    @Value("${monetbil.service-key}")
    private String monetbilServiceKey;

    @Value("${monetbil.service-secret}")
    private String monetbilServiceSecret;

    @Value("${monetbil.api-url:https://api.monetbil.com/payment/v1/placePayment}")
    private String monetbilApiUrl;

    @Value("${monetbil.don-notify-url:${monetbil.notify-url}}")
    private String donNotifyUrl;

    @Value("${monetbil.return-url}")
    private String monetbilReturnUrl;


    // ── Initier un don ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DonResponse initierDon(DonRequest request, Long utilisateurId) {
        if (request.getOperateur() != PaiementMode.MTN_MOBILE_MONEY
                && request.getOperateur() != PaiementMode.ORANGE_MONEY) {
            throw new BadRequestException("Opérateur non supporté. Choisissez MTN_MOBILE_MONEY ou ORANGE_MONEY.");
        }

        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new BadRequestException("Utilisateur introuvable."));

        String reference = "DON-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();

        // Numéro du DONATEUR à débiter (et non un numéro de réception) — c'est sur ce
        // téléphone que Monetbil pousse la fenêtre de confirmation USSD.
        String phone = buildPhone(request.getNumeroPaiement());

        BigDecimal montant = BigDecimal.valueOf(request.getMontant());

        Tirage tirage = (request.getTirageId() != null)
                ? tirageRepository.findById(request.getTirageId()).orElse(null)
                : null;

        Don don = Don.builder()
                .utilisateur(utilisateur)
                .montant(montant)
                .operateur(request.getOperateur())
                .statut(PaiementStatus.EN_ATTENTE)
                .referenceTransaction(reference)
                .numeroPaieur(request.getNumeroPaiement())
                .tirage(tirage)
                .build();
        don = donRepository.save(don);

        String operator = request.getOperateur() == PaiementMode.MTN_MOBILE_MONEY
                ? "CM_MTNMOBILEMONEY" : "CM_ORANGEMONEY";

        MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("service",     monetbilServiceKey);
        payload.add("amount",      String.valueOf(montant.longValue()));
        payload.add("phonenumber", phone);
        payload.add("operator",    operator);
        payload.add("item_ref",    reference);
        payload.add("notify_url",  donNotifyUrl);

        try {
            com.fasterxml.jackson.databind.JsonNode response =
                    monetbilGateway.callApi(monetbilApiUrl, payload);

            String status    = response.path("status").asText("");
            String paymentId = response.path("paymentId").asText(reference);

            if ("REQUEST_ACCEPTED".equals(status)) {
                don.setGatewayTransactionId(paymentId);
                don = donRepository.save(don);
                String instructions = "Une fenêtre de confirmation s'affiche sur votre téléphone — "
                        + "entrez votre code secret " + (request.getOperateur() == PaiementMode.MTN_MOBILE_MONEY
                        ? "MTN MoMo" : "Orange Money") + " pour valider votre don.";
                log.info("Don {} push initié pour {} FCFA", reference, montant.longValue());
                return toResponse(don, instructions);
            }

            if ("INVALID_MSISDN".equalsIgnoreCase(status)) {
                don.setStatut(PaiementStatus.ANNULE);
                don.setMessageOperateur("Numéro invalide : " + request.getNumeroPaiement());
                donRepository.save(don);
                throw new BadRequestException("Le numéro " + request.getNumeroPaiement()
                        + " n'est pas un numéro " + (request.getOperateur() == PaiementMode.MTN_MOBILE_MONEY
                        ? "MTN Mobile Money" : "Orange Money") + " valide. Vérifiez le numéro à débiter.");
            }

            log.warn("Monetbil placePayment don statut inattendu : {} — repli sur le lien de paiement", status);

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            // placePayment indisponible (ex. IP serveur défiée par le WAF Monetbil) → repli lien
            log.warn("[Don] placePayment indisponible ({}) — repli sur le lien de paiement", e.getMessage());
        }

        // Repli : lien de paiement widget. Le donateur finalise depuis SON navigateur
        // (son IP n'est pas bloquée, contrairement à celle du serveur). Le don reste
        // EN_ATTENTE ; le webhook Monetbil le confirmera après paiement.
        String widgetUrl = buildWidgetDonUrl(montant, phone, reference, utilisateurId);
        don.setGatewayPaymentUrl(widgetUrl);
        don = donRepository.save(don);
        log.info("Don {} — lien de paiement fourni ({} FCFA)", reference, montant.longValue());
        return toResponse(don,
                "Ouvrez le lien de paiement pour finaliser votre don en toute sécurité. "
                + "Dès la confirmation, vous recevrez votre remerciement.");
    }

    // Normalisation numéro camerounais → 237XXXXXXXXX (identique au flux paiement).
    private String buildPhone(String numeroPaiement) {
        String phone = numeroPaiement.replaceAll("[^0-9]", "");
        if (phone.startsWith("00237")) phone = phone.substring(5);
        else if (phone.startsWith("237") && phone.length() > 9) phone = phone.substring(3);
        return "237" + phone;
    }

    // Lien de paiement Monetbil (widget) — ouvert dans le navigateur du donateur.
    private String buildWidgetDonUrl(BigDecimal montant, String phone, String reference, Long userId) {
        return "https://www.monetbil.com/widget/v2.1/" + monetbilServiceKey
                + "?amount="      + montant.longValue()
                + "&phone="       + phone
                + "&locale=fr"
                + "&item_ref="    + URLEncoder.encode(reference, StandardCharsets.UTF_8)
                + "&payment_ref=" + URLEncoder.encode(reference, StandardCharsets.UTF_8)
                + "&user1=don"
                + "&user2="       + URLEncoder.encode("utilisateur_" + userId, StandardCharsets.UTF_8)
                + "&notify_url="  + URLEncoder.encode(donNotifyUrl, StandardCharsets.UTF_8)
                + "&return_url="  + URLEncoder.encode(monetbilReturnUrl, StandardCharsets.UTF_8);
    }

    // ── Statut (polling app : attente de confirmation du débit) ────────────────

    @Override
    @Transactional(readOnly = true)
    public DonStatutResponse getStatutDon(String reference, Long utilisateurId) {
        Don don = donRepository.findByReferenceTransaction(reference)
                .orElseThrow(() -> new BadRequestException("Don introuvable."));
        if (!don.getUtilisateur().getId().equals(utilisateurId)) {
            throw new BadRequestException("Ce don ne vous appartient pas.");
        }
        Utilisateur u = don.getUtilisateur();
        return DonStatutResponse.builder()
                .statut(don.getStatut())
                .montant(don.getMontant())
                .nomComplet((u.getPrenom() + " " + u.getNom()).trim())
                .build();
    }

    // ── Callback Monetbil ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public ApiResponse<String> traiterCallbackDon(Map<String, String> params) {
        String sign      = params.get("sign");
        String reference = params.get("item_ref");
        String txId      = params.get("payment_ref");
        String status    = params.getOrDefault("status", "");

        log.info("Callback don reçu — ref={} txId={} status={}", reference, txId, status);

        if (!verifierSignature(params, sign)) {
            log.warn("Signature Monetbil invalide pour le don {}", reference);
            return ApiResponse.<String>builder().success(false).message("Signature invalide").build();
        }

        Don don = null;
        if (reference != null && !reference.isBlank()) {
            don = donRepository.findByReferenceTransactionForUpdate(reference).orElse(null);
        }
        if (don == null && txId != null && !txId.isBlank()) {
            don = donRepository.findByGatewayTransactionIdForUpdate(txId).orElse(null);
        }
        if (don == null) {
            log.warn("Don introuvable pour ref={} txId={}", reference, txId);
            return ApiResponse.<String>builder().success(false).message("Don introuvable").build();
        }

        // Idempotence
        if (don.getStatut() == PaiementStatus.PAYE) {
            return ApiResponse.<String>builder().success(true).message("Déjà traité").build();
        }

        if (txId != null && !txId.isBlank() && (don.getGatewayTransactionId() == null || don.getGatewayTransactionId().isBlank())) {
            don.setGatewayTransactionId(txId);
        }

        switch (status.toUpperCase()) {
            case "SUCCESS" -> {
                don.setStatut(PaiementStatus.PAYE);
                don.setMessageOperateur(params.getOrDefault("message", "Paiement confirmé"));
                log.info("Don {} confirmé — merci !", don.getReferenceTransaction());
                // Notification de remerciement au donateur
                notificationService.creerNotification(
                        don.getUtilisateur(),
                        null,
                        "💝 Merci pour votre don !",
                        "Votre don de " + don.getMontant().longValue() + " FCFA a bien été reçu. Merci pour votre générosité !",
                        NotificationType.DON_CONFIRME
                );
            }
            case "FAILED", "CANCELLED" -> {
                don.setStatut(PaiementStatus.ANNULE);
                don.setMessageOperateur(params.getOrDefault("message", "Paiement annulé"));
            }
            default -> {
                log.info("Statut don non terminal : {}", status);
                return ApiResponse.<String>builder().success(true).message("Statut intermédiaire").build();
            }
        }

        donRepository.save(don);
        return ApiResponse.<String>builder().success(true).message("Don traité").build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean verifierSignature(Map<String, String> payload, String sign) {
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
            return MessageDigest.isEqual(
                    hex.toString().toLowerCase().getBytes(StandardCharsets.UTF_8),
                    sign.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Erreur vérification signature don : {}", e.getMessage());
            return false;
        }
    }

    private DonResponse toResponse(Don d, String instructions) {
        return DonResponse.builder()
                .id(d.getId())
                .referenceTransaction(d.getReferenceTransaction())
                .montant(d.getMontant())
                .devise(d.getDevise())
                .operateur(d.getOperateur())
                .statut(d.getStatut())
                .urlPaiement(d.getGatewayPaymentUrl())
                .messageOperateur(d.getMessageOperateur())
                .instructions(instructions)
                .createdAt(d.getCreatedAt())
                .build();
    }
}
