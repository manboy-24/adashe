package com.tontine.service.impl;

import com.tontine.config.MobileMoneyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Intégration Orange Money Cameroun
 * Documentation: https://developer.orange.com/apis/orange-money-webpay-cm
 * Utilise le flux WebPay (redirection ou API directe)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrangeMoneyService {

    private final MobileMoneyConfig config;
    private final RestTemplate restTemplate;

    /**
     * Étape 1: Obtenir le token OAuth2 Orange
     */
    public String obtenirAccessToken() {
        MobileMoneyConfig.Orange orange = config.getOrange();

        String credentials = orange.getClientId() + ":" + orange.getClientSecret();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.orange.com/oauth/v3/token",
                new HttpEntity<>(body, headers),
                Map.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            log.error("Erreur Orange Money token: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Étape 2: Initier un paiement Orange Money
     * Envoie une notification USSD au client
     */
    public Map<String, Object> initierPaiement(
            String telephone,
            BigDecimal montant,
            String referenceInterne,
            String description
    ) {
        MobileMoneyConfig.Orange orange = config.getOrange();
        String accessToken = obtenirAccessToken();

        if (accessToken == null) {
            return Map.of("success", false, "message", "Impossible d'obtenir le token Orange Money");
        }

        String telNormalise = normaliserTelCameroun(telephone);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("merchant_key", orange.getMerchantKey());
        body.put("currency", orange.getCurrency());
        body.put("order_id", referenceInterne);
        body.put("amount", montant.intValue());
        body.put("return_url", orange.getReturnUrl());
        body.put("cancel_url", orange.getCancelUrl());
        body.put("notif_url", orange.getNotifUrl());
        body.put("lang", "fr");
        body.put("reference", description);
        // Pour le paiement direct (sans redirection)
        body.put("msisdn", telNormalise);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                orange.getBaseUrl() + "/cashinitsession",
                new HttpEntity<>(body, headers),
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> resp = response.getBody();
                String payToken = (String) resp.getOrDefault("pay_token", "");
                String notifToken = (String) resp.getOrDefault("notif_token", "");

                log.info("Orange Money initié: {} - token: {}", referenceInterne, payToken);
                return Map.of(
                    "success", true,
                    "payToken", payToken,
                    "notifToken", notifToken,
                    "referenceOperateur", payToken,
                    "message", "Confirmez le paiement Orange Money sur votre téléphone (#150*50#)"
                );
            } else {
                return Map.of("success", false, "message", "Échec Orange Money: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Erreur Orange Money initierPaiement: {}", e.getMessage());
            return Map.of("success", false, "message", "Erreur Orange Money: " + e.getMessage());
        }
    }

    /**
     * Étape 3: Vérifier le statut du paiement Orange Money
     */
    public Map<String, Object> verifierStatut(String payToken) {
        MobileMoneyConfig.Orange orange = config.getOrange();
        String accessToken = obtenirAccessToken();

        if (accessToken == null) {
            return Map.of("statut", "ERREUR", "message", "Token Orange indisponible");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
            "merchant_key", orange.getMerchantKey(),
            "pay_token", payToken
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                orange.getBaseUrl() + "/cashinquiry",
                new HttpEntity<>(body, headers),
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> resp = response.getBody();
                String statut = (String) resp.getOrDefault("status", "PENDING");
                String txnId  = (String) resp.getOrDefault("txnid", "");
                return Map.of(
                    "statut", convertirStatutOrange(statut),
                    "transactionId", txnId,
                    "message", resp.getOrDefault("message", "")
                );
            }
        } catch (Exception e) {
            log.error("Erreur vérification statut Orange: {}", e.getMessage());
        }
        return Map.of("statut", "EN_ATTENTE", "message", "Vérification en cours...");
    }

    private String convertirStatutOrange(String statut) {
        return switch (statut.toUpperCase()) {
            case "SUCCESS", "SUCCESSFULL" -> "SUCCES";
            case "FAILED", "FAILURE"      -> "ECHEC";
            case "EXPIRED"               -> "EXPIRE";
            default                      -> "EN_ATTENTE";
        };
    }

    private String normaliserTelCameroun(String telephone) {
        String t = telephone.replaceAll("[^0-9]", "");
        if (t.startsWith("237")) t = t.substring(3);
        return "237" + t;
    }
}
