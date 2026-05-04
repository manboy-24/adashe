package com.tontine.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tontine.config.MobileMoneyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

/**
 * Intégration MTN Mobile Money (MoMo) API - Cameroun
 * Documentation: https://momodeveloper.mtn.com/
 * Collection: Collections API (pour recevoir des paiements)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MtnMobileMoneyService {

    private final MobileMoneyConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Étape 1: Demander un token d'accès OAuth2
     */
    public String obtenirAccessToken() {
        MobileMoneyConfig.Mtn mtn = config.getMtn();
        String credentials = mtn.getApiUser() + ":" + mtn.getApiKey();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        headers.set("Ocp-Apim-Subscription-Key", mtn.getSubscriptionKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                mtn.getBaseUrl() + "/collection/token/",
                entity,
                Map.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            log.error("Erreur MTN token: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Étape 2: Initier une demande de paiement (Request to Pay)
     * L'utilisateur reçoit une notification USSD sur son téléphone
     */
    public Map<String, Object> requestToPay(
            String telephone,
            BigDecimal montant,
            String referenceInterne,
            String description
    ) {
        MobileMoneyConfig.Mtn mtn = config.getMtn();
        String accessToken = obtenirAccessToken();

        if (accessToken == null) {
            return Map.of("success", false, "message", "Impossible d'obtenir le token MTN MoMo");
        }

        // Normaliser le numéro camerounais (retirer +237 ou 237)
        String telNormalise = normaliserTelCameroun(telephone);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Ocp-Apim-Subscription-Key", mtn.getSubscriptionKey());
        headers.set("X-Reference-Id", referenceInterne);
        headers.set("X-Target-Environment", mtn.getEnvironment());
        if (mtn.getCallbackUrl() != null) {
            headers.set("X-Callback-Url", mtn.getCallbackUrl());
        }
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", montant.toPlainString());
        body.put("currency", mtn.getCurrency());
        body.put("externalId", referenceInterne);
        body.put("payer", Map.of(
            "partyIdType", "MSISDN",
            "partyId", telNormalise
        ));
        body.put("payerMessage", description);
        body.put("payeeNote", "Cotisation Tontine+ - " + referenceInterne);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                mtn.getBaseUrl() + "/collection/v1_0/requesttopay",
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                log.info("MTN MoMo request-to-pay initié: {}", referenceInterne);
                return Map.of(
                    "success", true,
                    "referenceOperateur", referenceInterne,
                    "message", "Demande de paiement envoyée. Vérifiez votre téléphone MTN."
                );
            } else {
                return Map.of("success", false, "message", "Échec de la demande MTN MoMo: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Erreur MTN requestToPay: {}", e.getMessage());
            return Map.of("success", false, "message", "Erreur réseau MTN: " + e.getMessage());
        }
    }

    /**
     * Étape 3: Vérifier le statut d'un paiement
     */
    public Map<String, Object> verifierStatut(String referenceInterne) {
        MobileMoneyConfig.Mtn mtn = config.getMtn();
        String accessToken = obtenirAccessToken();

        if (accessToken == null) {
            return Map.of("statut", "ERREUR", "message", "Token MTN indisponible");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Ocp-Apim-Subscription-Key", mtn.getSubscriptionKey());
        headers.set("X-Target-Environment", mtn.getEnvironment());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                mtn.getBaseUrl() + "/collection/v1_0/requesttopay/" + referenceInterne,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String statut = (String) body.getOrDefault("status", "PENDING");
                // MTN retourne: PENDING | SUCCESSFUL | FAILED
                return Map.of(
                    "statut", convertirStatutMtn(statut),
                    "transactionId", body.getOrDefault("financialTransactionId", ""),
                    "message", body.getOrDefault("reason", "")
                );
            }
        } catch (Exception e) {
            log.error("Erreur vérification statut MTN: {}", e.getMessage());
        }
        return Map.of("statut", "EN_ATTENTE", "message", "Vérification en cours...");
    }

    private String convertirStatutMtn(String statut) {
        return switch (statut.toUpperCase()) {
            case "SUCCESSFUL" -> "SUCCES";
            case "FAILED"     -> "ECHEC";
            default           -> "EN_ATTENTE";
        };
    }

    private String normaliserTelCameroun(String telephone) {
        String t = telephone.replaceAll("[^0-9]", "");
        if (t.startsWith("237")) t = t.substring(3);
        return "237" + t; // MTN attend le format 2376XXXXXXXX
    }
}
