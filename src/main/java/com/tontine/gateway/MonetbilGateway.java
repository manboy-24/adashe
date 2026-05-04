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
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
        return objectMapper.readTree(response.getBody());
    }

    // Appelé quand le circuit est ouvert ou que toutes les tentatives ont échoué
    public JsonNode fallback(String apiUrl, MultiValueMap<String, String> params, Exception e) {
        log.error("[Monetbil] Circuit ouvert ou échec définitif — {}", e.getMessage());
        throw new ServiceUnavailableException(
                "Le service de paiement est temporairement indisponible. Réessayez dans quelques minutes.");
    }
}
