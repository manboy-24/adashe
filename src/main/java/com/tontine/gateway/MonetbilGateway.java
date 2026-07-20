package com.tontine.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tontine.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Couche d'accès à l'API Monetbil protégée par Circuit Breaker + Retry.
 *
 * Circuit Breaker « monetbil » :
 *  - S'ouvre après 50 % d'échecs sur une fenêtre de 5 appels
 *  - Reste ouvert 30 secondes avant de tester à nouveau (half-open)
 *
 * Retry « monetbil » :
 *  - 2 tentatives automatiques avec 1 seconde d'attente entre chaque
 *  - Ne s'applique qu'aux exceptions réseau (pas aux erreurs métier 4xx)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonetbilGateway {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "monetbil", fallbackMethod = "fallback")
    @Retry(name = "monetbil")
    public JsonNode callApi(String apiUrl, MultiValueMap<String, String> params) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        headers.set("Accept", "application/json, text/plain, */*");
        headers.set("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8");
        headers.set("Origin", "https://www.monetbil.com");
        headers.set("Referer", "https://www.monetbil.com/");
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
            String body = response.getBody();
            if (body != null && body.startsWith("<")) {
                throw new RuntimeException("Monetbil CAPTCHA — réponse HTML inattendue. IP serveur bloquée.");
            }
            return objectMapper.readTree(body);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Erreur métier 4xx (ex. INVALID_MSISDN) : renvoyer le corps JSON au métier
            // au lieu de déclencher le fallback « service indisponible »
            String errBody = e.getResponseBodyAsString();
            if (errBody != null && !errBody.isBlank() && errBody.trim().startsWith("{")) {
                log.warn("[Monetbil] Réponse {} : {}", e.getStatusCode(), errBody);
                return objectMapper.readTree(errBody);
            }
            throw e;
        }
    }

    // Appelé quand le circuit est ouvert ou que toutes les tentatives ont échoué
    public JsonNode fallback(String apiUrl, MultiValueMap<String, String> params, Exception e) {
        log.error("[Monetbil] Circuit ouvert ou échec définitif — {}", e.getMessage());
        throw new ServiceUnavailableException(
                "Le service de paiement est temporairement indisponible. Réessayez dans quelques minutes.");
    }
}
