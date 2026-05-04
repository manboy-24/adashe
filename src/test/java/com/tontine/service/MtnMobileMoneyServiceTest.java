package com.tontine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tontine.config.MobileMoneyConfig;
import com.tontine.service.impl.MtnMobileMoneyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MtnMobileMoneyServiceTest {

    @Mock private MobileMoneyConfig config;
    @Mock private RestTemplate      restTemplate;
    @Mock private ObjectMapper      objectMapper;

    @InjectMocks
    private MtnMobileMoneyService mtnService;

    private MobileMoneyConfig.Mtn mtnConfig;

    @BeforeEach
    void setUp() {
        mtnConfig = new MobileMoneyConfig.Mtn();
        mtnConfig.setBaseUrl("https://sandbox.momodeveloper.mtn.com");
        mtnConfig.setSubscriptionKey("sub-key");
        mtnConfig.setApiUser("api-user");
        mtnConfig.setApiKey("api-key");
        mtnConfig.setEnvironment("sandbox");
        mtnConfig.setCurrency("XAF");
        mtnConfig.setCallbackUrl("http://localhost/callback");

        when(config.getMtn()).thenReturn(mtnConfig);
    }

    // ── obtenirAccessToken ────────────────────────────────────────────────────

    @Test
    void obtenirAccessToken_succes_retourne_token() {
        Map<String, Object> tokenResp = Map.of("access_token", "mtn-bearer-token");
        when(restTemplate.postForEntity(contains("/collection/token/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));

        String token = mtnService.obtenirAccessToken();

        assertThat(token).isEqualTo("mtn-bearer-token");
    }

    @Test
    void obtenirAccessToken_echec_retourne_null() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        String token = mtnService.obtenirAccessToken();

        assertThat(token).isNull();
    }

    // ── requestToPay ─────────────────────────────────────────────────────────

    @Test
    void requestToPay_succes_retourne_map_success() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        when(restTemplate.postForEntity(contains("/collection/token/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/requesttopay"), any(), eq(String.class)))
                .thenReturn(ResponseEntity.accepted().build());

        Map<String, Object> result = mtnService.requestToPay(
                "699000001", new BigDecimal("5000"), "REF-001", "Cotisation tontine");

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("message").toString()).contains("téléphone MTN");
    }

    @Test
    void requestToPay_token_null_retourne_erreur() {
        when(restTemplate.postForEntity(contains("/collection/token/"), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Token unavailable"));

        Map<String, Object> result = mtnService.requestToPay(
                "699000001", new BigDecimal("5000"), "REF-001", "Cotisation");

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message").toString()).contains("token");
    }

    @Test
    void requestToPay_api_non_202_retourne_echec() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        when(restTemplate.postForEntity(contains("/collection/token/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/requesttopay"), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());

        Map<String, Object> result = mtnService.requestToPay(
                "699000001", new BigDecimal("5000"), "REF-001", "Cotisation");

        assertThat(result.get("success")).isEqualTo(false);
    }

    @Test
    void requestToPay_exception_reseau_retourne_erreur() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        when(restTemplate.postForEntity(contains("/collection/token/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/requesttopay"), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Timeout"));

        Map<String, Object> result = mtnService.requestToPay(
                "699000001", new BigDecimal("5000"), "REF-001", "Cotisation");

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message").toString()).contains("Erreur réseau");
    }

    // ── verifierStatut ────────────────────────────────────────────────────────

    @Test
    void verifierStatut_successful_retourne_succes() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        Map<String, Object> statusResp = Map.of(
                "status", "SUCCESSFUL",
                "financialTransactionId", "TXN-123",
                "reason", "");

        when(restTemplate.postForEntity(contains("/collection/token/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.exchange(contains("/requesttopay/REF-001"), any(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(statusResp));

        Map<String, Object> result = mtnService.verifierStatut("REF-001");

        assertThat(result.get("statut")).isEqualTo("SUCCES");
        assertThat(result.get("transactionId")).isEqualTo("TXN-123");
    }

    @Test
    void verifierStatut_failed_retourne_echec() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        Map<String, Object> statusResp = Map.of("status", "FAILED", "financialTransactionId", "", "reason", "Fonds insuffisants");

        when(restTemplate.postForEntity(contains("/collection/token/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(statusResp));

        Map<String, Object> result = mtnService.verifierStatut("REF-001");

        assertThat(result.get("statut")).isEqualTo("ECHEC");
    }

    @Test
    void verifierStatut_pending_retourne_en_attente() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        Map<String, Object> statusResp = Map.of("status", "PENDING", "financialTransactionId", "", "reason", "");

        when(restTemplate.postForEntity(contains("/collection/token/"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(statusResp));

        Map<String, Object> result = mtnService.verifierStatut("REF-001");

        assertThat(result.get("statut")).isEqualTo("EN_ATTENTE");
    }

    @Test
    void verifierStatut_token_null_retourne_erreur() {
        when(restTemplate.postForEntity(contains("/collection/token/"), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Token fail"));

        Map<String, Object> result = mtnService.verifierStatut("REF-001");

        assertThat(result.get("statut")).isEqualTo("ERREUR");
    }
}
