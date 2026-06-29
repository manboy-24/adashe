package com.tontine.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tontine.config.MobileMoneyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

/**
 * Intégration MTN MADAPI Payments V1 — Cameroun production
 * Base URL : https://api.mtn.com/v1
 * Auth : OAuth2 client_credentials (Consumer Key / Consumer Secret)
 * Endpoint : POST /payments  (debit request vers le wallet du payeur)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MtnMobileMoneyService {

    private final MobileMoneyConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** Obtenir un Bearer token via OAuth2 client_credentials */
    public String obtenirAccessToken() {
        MobileMoneyConfig.Mtn mtn = config.getMtn();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // grant_type en query param (Apigee MTN) — client_id/secret en form body
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id",     mtn.getApiUser());
        body.add("client_secret", mtn.getApiKey());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.mtn.com/v1/oauth/access_token?grant_type=client_credentials",
                new HttpEntity<>(body, headers),
                Map.class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String token = (String) response.getBody().get("access_token");
                log.info("[MTN] Token obtenu avec succès");
                return token;
            }
            log.error("[MTN] Token endpoint HTTP {}", response.getStatusCode());
        } catch (Exception e) {
            log.error("[MTN] Erreur token: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Initier un paiement MoMo via MADAPI POST /payments.
     * MTN envoie une notification USSD/push directement sur le téléphone du payeur.
     * Retourne la map : success, referenceOperateur, message.
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

        String msisdn = normaliserTelCameroun(telephone);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("countryCode",     "CM");
        headers.set("transactionId",   referenceInterne);

        Map<String, Object> payer = new LinkedHashMap<>();
        payer.put("payerId",     msisdn);
        payer.put("payerIdType", "MSISDN");
        payer.put("payerNote",   description);

        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("amount", montant.toPlainString());
        amount.put("units",  mtn.getCurrency());   // XAF

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("externalTransactionId", referenceInterne);
        requestBody.put("description",           description);
        requestBody.put("amount",                amount);
        requestBody.put("payer",                 payer);
        requestBody.put("transactionType",       "DEBIT_REQUEST");
        requestBody.put("callbackURL",           mtn.getCallbackUrl());
        requestBody.put("countryCode",           "CM");

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                mtn.getBaseUrl() + "/payments",
                new HttpEntity<>(requestBody, headers),
                String.class
            );

            log.info("[MTN] POST /payments → HTTP {}", response.getStatusCode());

            if (response.getStatusCode() == HttpStatus.ACCEPTED
                    || response.getStatusCode().is2xxSuccessful()) {

                String transactionId = referenceInterne;
                try {
                    JsonNode node = objectMapper.readTree(response.getBody());
                    transactionId = node.path("transactionId").asText(referenceInterne);
                } catch (Exception ignored) {}

                log.info("[MTN] Paiement push initié: ref={} msisdn={}", referenceInterne, msisdn);
                return Map.of(
                    "success",            true,
                    "referenceOperateur", transactionId,
                    "message",            "Demande envoyée. Vérifiez votre téléphone MTN MoMo."
                );
            }

            String errMsg = response.getBody() != null ? response.getBody() : "Erreur MTN";
            log.warn("[MTN] Échec paiement: HTTP {} — {}", response.getStatusCode(), errMsg);
            return Map.of("success", false, "message", "Échec MTN MoMo: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("[MTN] Erreur requestToPay: {}", e.getMessage());
            return Map.of("success", false, "message", "Erreur réseau MTN: " + e.getMessage());
        }
    }

    /** Vérifier le statut d'un paiement via GET /payments/{transactionId} */
    public Map<String, Object> verifierStatut(String transactionId) {
        MobileMoneyConfig.Mtn mtn = config.getMtn();
        String accessToken = obtenirAccessToken();

        if (accessToken == null) {
            return Map.of("statut", "ERREUR", "message", "Token MTN indisponible");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("countryCode", "CM");

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                mtn.getBaseUrl() + "/payments/" + transactionId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                String statut = String.valueOf(body.getOrDefault("status", "PENDING"));
                return Map.of(
                    "statut",        convertirStatut(statut),
                    "transactionId", body.getOrDefault("transactionId", ""),
                    "message",       body.getOrDefault("statusDescription", "")
                );
            }
        } catch (Exception e) {
            log.error("[MTN] Erreur vérification statut: {}", e.getMessage());
        }
        return Map.of("statut", "EN_ATTENTE", "message", "Vérification en cours...");
    }

    private String convertirStatut(String statut) {
        return switch (statut.toUpperCase()) {
            case "SUCCESSFUL", "SUCCESS", "COMPLETED" -> "SUCCES";
            case "FAILED", "FAILURE"                  -> "ECHEC";
            default                                   -> "EN_ATTENTE";
        };
    }

    private String normaliserTelCameroun(String telephone) {
        String t = telephone.replaceAll("[^0-9]", "");
        if (t.startsWith("237")) t = t.substring(3);
        return "237" + t;
    }
}
