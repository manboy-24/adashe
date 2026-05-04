package com.tontine.service;

import com.tontine.config.MobileMoneyConfig;
import com.tontine.service.impl.OrangeMoneyService;
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
class OrangeMoneyServiceTest {

    @Mock private MobileMoneyConfig config;
    @Mock private RestTemplate      restTemplate;

    @InjectMocks
    private OrangeMoneyService orangeService;

    private MobileMoneyConfig.Orange orangeConfig;

    @BeforeEach
    void setUp() {
        orangeConfig = new MobileMoneyConfig.Orange();
        orangeConfig.setBaseUrl("https://api.orange.com/orange-money-webpay/cm/v1");
        orangeConfig.setClientId("client-id");
        orangeConfig.setClientSecret("client-secret");
        orangeConfig.setMerchantKey("merch-key");
        orangeConfig.setCurrency("XAF");
        orangeConfig.setReturnUrl("http://localhost/return");
        orangeConfig.setCancelUrl("http://localhost/cancel");
        orangeConfig.setNotifUrl("http://localhost/notif");

        when(config.getOrange()).thenReturn(orangeConfig);
    }

    // ── obtenirAccessToken ────────────────────────────────────────────────────

    @Test
    void obtenirAccessToken_succes_retourne_token() {
        Map<String, Object> tokenResp = Map.of("access_token", "orange-bearer-token");
        when(restTemplate.postForEntity(eq("https://api.orange.com/oauth/v3/token"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));

        String token = orangeService.obtenirAccessToken();

        assertThat(token).isEqualTo("orange-bearer-token");
    }

    @Test
    void obtenirAccessToken_echec_retourne_null() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        String token = orangeService.obtenirAccessToken();

        assertThat(token).isNull();
    }

    @Test
    void obtenirAccessToken_reponse_non_2xx_retourne_null() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

        String token = orangeService.obtenirAccessToken();

        assertThat(token).isNull();
    }

    // ── initierPaiement ───────────────────────────────────────────────────────

    @Test
    void initierPaiement_succes_retourne_pay_token() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        Map<String, Object> sessionResp = Map.of(
                "pay_token", "PAY-TOKEN-123",
                "notif_token", "NOTIF-TOKEN-456"
        );

        when(restTemplate.postForEntity(eq("https://api.orange.com/oauth/v3/token"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/cashinitsession"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(sessionResp));

        Map<String, Object> result = orangeService.initierPaiement(
                "699000001", new BigDecimal("5000"), "REF-001", "Cotisation");

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("payToken")).isEqualTo("PAY-TOKEN-123");
        assertThat(result.get("message").toString()).contains("Orange Money");
    }

    @Test
    void initierPaiement_token_null_retourne_erreur() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Token unavailable"));

        Map<String, Object> result = orangeService.initierPaiement(
                "699000001", new BigDecimal("5000"), "REF-001", "Cotisation");

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message").toString()).contains("token");
    }

    @Test
    void initierPaiement_api_non_2xx_retourne_echec() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        when(restTemplate.postForEntity(eq("https://api.orange.com/oauth/v3/token"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/cashinitsession"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());

        Map<String, Object> result = orangeService.initierPaiement(
                "699000001", new BigDecimal("5000"), "REF-001", "Cotisation");

        assertThat(result.get("success")).isEqualTo(false);
    }

    @Test
    void initierPaiement_exception_reseau_retourne_erreur() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        when(restTemplate.postForEntity(eq("https://api.orange.com/oauth/v3/token"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/cashinitsession"), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Timeout"));

        Map<String, Object> result = orangeService.initierPaiement(
                "699000001", new BigDecimal("5000"), "REF-001", "Cotisation");

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message").toString()).contains("Erreur Orange Money");
    }

    @Test
    void initierPaiement_normalise_telephone_sans_indicatif() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        Map<String, Object> sessionResp = Map.of("pay_token", "TOK", "notif_token", "N");

        when(restTemplate.postForEntity(eq("https://api.orange.com/oauth/v3/token"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/cashinitsession"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(sessionResp));

        // téléphone sans préfixe 237 — la méthode doit le normaliser
        Map<String, Object> result = orangeService.initierPaiement(
                "699000001", new BigDecimal("1000"), "REF-002", "Test");

        assertThat(result.get("success")).isEqualTo(true);
    }

    // ── verifierStatut ────────────────────────────────────────────────────────

    @Test
    void verifierStatut_success_retourne_succes() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        Map<String, Object> statusResp = Map.of("status", "SUCCESS", "txnid", "TXN-789", "message", "OK");

        when(restTemplate.postForEntity(eq("https://api.orange.com/oauth/v3/token"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/cashinquiry"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(statusResp));

        Map<String, Object> result = orangeService.verifierStatut("PAY-TOKEN-123");

        assertThat(result.get("statut")).isEqualTo("SUCCES");
        assertThat(result.get("transactionId")).isEqualTo("TXN-789");
    }

    @Test
    void verifierStatut_successfull_retourne_succes() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        Map<String, Object> statusResp = Map.of("status", "SUCCESSFULL", "txnid", "", "message", "");

        when(restTemplate.postForEntity(eq("https://api.orange.com/oauth/v3/token"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/cashinquiry"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(statusResp));

        Map<String, Object> result = orangeService.verifierStatut("PAY-TOKEN-123");

        assertThat(result.get("statut")).isEqualTo("SUCCES");
    }

    @Test
    void verifierStatut_failed_retourne_echec() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        Map<String, Object> statusResp = Map.of("status", "FAILED", "txnid", "", "message", "Refusé");

        when(restTemplate.postForEntity(eq("https://api.orange.com/oauth/v3/token"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/cashinquiry"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(statusResp));

        Map<String, Object> result = orangeService.verifierStatut("PAY-TOKEN-123");

        assertThat(result.get("statut")).isEqualTo("ECHEC");
    }

    @Test
    void verifierStatut_expired_retourne_expire() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");
        Map<String, Object> statusResp = Map.of("status", "EXPIRED", "txnid", "", "message", "");

        when(restTemplate.postForEntity(eq("https://api.orange.com/oauth/v3/token"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/cashinquiry"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(statusResp));

        Map<String, Object> result = orangeService.verifierStatut("PAY-TOKEN-123");

        assertThat(result.get("statut")).isEqualTo("EXPIRE");
    }

    @Test
    void verifierStatut_token_null_retourne_erreur() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Token fail"));

        Map<String, Object> result = orangeService.verifierStatut("PAY-TOKEN-123");

        assertThat(result.get("statut")).isEqualTo("ERREUR");
    }

    @Test
    void verifierStatut_exception_reseau_retourne_en_attente() {
        Map<String, Object> tokenResp = Map.of("access_token", "bearer-token");

        when(restTemplate.postForEntity(eq("https://api.orange.com/oauth/v3/token"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(tokenResp));
        when(restTemplate.postForEntity(contains("/cashinquiry"), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Network error"));

        Map<String, Object> result = orangeService.verifierStatut("PAY-TOKEN-123");

        assertThat(result.get("statut")).isEqualTo("EN_ATTENTE");
    }
}
