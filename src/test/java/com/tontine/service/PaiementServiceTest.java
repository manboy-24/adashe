package com.tontine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tontine.dto.request.PaiementMobileMoneyRequest;
import com.tontine.dto.response.ApiResponse;
import com.tontine.dto.response.PaiementResponse;
import com.tontine.entity.*;
import com.tontine.enums.*;
import com.tontine.exception.BadRequestException;
import com.tontine.exception.ForbiddenException;
import com.tontine.gateway.MonetbilGateway;
import com.tontine.repository.*;
import com.tontine.service.impl.PaiementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaiementServiceTest {

    @Mock private PaiementRepository      paiementRepository;
    @Mock private MembreTontineRepository membreRepository;
    @Mock private CotisationRepository    cotisationRepository;
    @Mock private TontineRepository       tontineRepository;
    @Mock private UtilisateurRepository   utilisateurRepository;
    @Mock private NotificationService     notificationService;
    @Mock private MonetbilGateway         monetbilGateway;

    @InjectMocks
    private PaiementServiceImpl paiementService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SERVICE_SECRET = "test-secret";

    private Utilisateur  utilisateur;
    private Tontine      tontine;
    private MembreTontine membre;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paiementService, "monetbilServiceKey",    "test-key");
        ReflectionTestUtils.setField(paiementService, "monetbilServiceSecret", SERVICE_SECRET);
        ReflectionTestUtils.setField(paiementService, "monetbilApiUrl",        "http://localhost/test");
        ReflectionTestUtils.setField(paiementService, "monetbilNotifyUrl",     "http://localhost/notify");
        ReflectionTestUtils.setField(paiementService, "monetbilReturnUrl",     "http://localhost/return");
        ReflectionTestUtils.setField(paiementService, "objectMapper",          objectMapper);

        utilisateur = Utilisateur.builder()
                .id(1L).nom("Kamga").prenom("Paul").telephone("699000001").build();

        tontine = Tontine.builder()
                .id(10L).nom("Tontine Test")
                .montantContribution(new BigDecimal("5000"))
                .devise("XAF").cycleActuel(1)
                .statut(TontineStatus.ACTIVE)
                .createur(utilisateur).build();

        membre = MembreTontine.builder()
                .id(100L).utilisateur(utilisateur).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.ACTIF).actif(true).build();
    }

    // ── initierPaiement ───────────────────────────────────────────────────────

    @Test
    void initierPaiement_succes_mtn() throws Exception {
        ObjectNode apiResp = objectMapper.createObjectNode();
        apiResp.put("success", 1);
        apiResp.put("widget_url", "https://pay.monetbil.com/widget/abc");
        apiResp.put("payment_ref", "PAY-123");

        when(membreRepository.findById(100L)).thenReturn(Optional.of(membre));
        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(paiementRepository.save(any())).thenAnswer(inv -> { Paiement p = inv.getArgument(0); p.setId(50L); return p; });
        when(monetbilGateway.callApi(anyString(), any())).thenReturn(apiResp);

        PaiementResponse result = paiementService.initierPaiement(buildRequest(PaiementMode.MTN_MOBILE_MONEY), 1L);

        assertThat(result.getStatut()).isEqualTo(PaiementStatus.EN_ATTENTE);
        assertThat(result.getUrlPaiement()).contains("monetbil");
        verify(paiementRepository, atLeast(2)).save(any());
    }

    @Test
    void initierPaiement_api_echoue_leve_exception() throws Exception {
        ObjectNode apiResp = objectMapper.createObjectNode();
        apiResp.put("success", 0);
        apiResp.put("message", "Numéro invalide");

        when(membreRepository.findById(100L)).thenReturn(Optional.of(membre));
        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(paiementRepository.save(any())).thenAnswer(inv -> { Paiement p = inv.getArgument(0); p.setId(50L); return p; });
        when(monetbilGateway.callApi(anyString(), any())).thenReturn(apiResp);

        assertThatThrownBy(() -> paiementService.initierPaiement(buildRequest(PaiementMode.ORANGE_MONEY), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Numéro invalide");

        // 2 saves : création (EN_ATTENTE) + marquage ANNULE
        // argThat capte la référence mutée : les deux calls matchent → on vérifie times(2)
        verify(paiementRepository, times(2)).save(any(Paiement.class));
    }

    @Test
    void initierPaiement_pour_autre_membre_leve_exception() {
        Utilisateur autreUser = Utilisateur.builder().id(99L).build();
        MembreTontine autreMembre = MembreTontine.builder()
                .id(100L).utilisateur(autreUser).tontine(tontine).build();

        when(membreRepository.findById(100L)).thenReturn(Optional.of(autreMembre));
        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));

        assertThatThrownBy(() -> paiementService.initierPaiement(buildRequest(PaiementMode.MTN_MOBILE_MONEY), 1L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void initierPaiement_operateur_non_supporte_leve_exception() {
        when(membreRepository.findById(100L)).thenReturn(Optional.of(membre));
        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));

        assertThatThrownBy(() -> paiementService.initierPaiement(buildRequest(PaiementMode.ESPECES), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Opérateur non supporté");
    }

    @Test
    void initierPaiement_exception_reseau_annule_paiement() throws Exception {
        when(membreRepository.findById(100L)).thenReturn(Optional.of(membre));
        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(paiementRepository.save(any())).thenAnswer(inv -> { Paiement p = inv.getArgument(0); p.setId(50L); return p; });
        when(monetbilGateway.callApi(anyString(), any())).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> paiementService.initierPaiement(buildRequest(PaiementMode.MTN_MOBILE_MONEY), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("indisponible");

        verify(paiementRepository, times(2)).save(any(Paiement.class));
    }

    // ── traiterCallbackMonetbil ───────────────────────────────────────────────

    @Test
    void callback_success_enregistre_cotisation() {
        Paiement paiement = paiementEnAttente();

        when(paiementRepository.findByReferenceTransactionForUpdate("REF-001"))
                .thenReturn(Optional.of(paiement));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(anyLong(), anyLong(), anyInt()))
                .thenReturn(Optional.empty());
        when(cotisationRepository.save(any())).thenAnswer(inv -> { Cotisation c = inv.getArgument(0); c.setId(200L); return c; });
        when(paiementRepository.save(any())).thenReturn(paiement);

        ApiResponse<String> result = paiementService.traiterCallbackMonetbil(
                buildSignedPayload("REF-001", "PAY-123", "SUCCESS"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(paiement.getStatut()).isEqualTo(PaiementStatus.PAYE);
        verify(cotisationRepository).save(any(Cotisation.class));
        verify(notificationService).creerNotification(any(), any(), any(), any(), any());
    }

    @Test
    void callback_failed_marque_annule() {
        Paiement paiement = paiementEnAttente();

        when(paiementRepository.findByReferenceTransactionForUpdate("REF-001"))
                .thenReturn(Optional.of(paiement));
        when(paiementRepository.save(any())).thenReturn(paiement);

        ApiResponse<String> result = paiementService.traiterCallbackMonetbil(
                buildSignedPayload("REF-001", "PAY-123", "FAILED"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(paiement.getStatut()).isEqualTo(PaiementStatus.ANNULE);
        verify(cotisationRepository, never()).save(any());
    }

    @Test
    void callback_idempotent_deja_paye_ne_retraite_pas() {
        Paiement paiement = Paiement.builder()
                .id(50L).membre(membre).montant(new BigDecimal("5000"))
                .statut(PaiementStatus.PAYE)
                .referenceTransaction("REF-001").build();

        when(paiementRepository.findByReferenceTransactionForUpdate("REF-001"))
                .thenReturn(Optional.of(paiement));

        ApiResponse<String> result = paiementService.traiterCallbackMonetbil(
                buildSignedPayload("REF-001", "PAY-123", "SUCCESS"));

        assertThat(result.getMessage()).contains("Déjà traité");
        verify(cotisationRepository, never()).save(any());
        verify(paiementRepository, never()).save(any());
    }

    @Test
    void callback_signature_invalide_rejete() {
        // Payload construit manuellement avec une fausse signature
        Map<String, String> payload = new HashMap<>();
        payload.put("item_ref",    "REF-001");
        payload.put("payment_ref", "PAY-123");
        payload.put("status",      "SUCCESS");
        payload.put("sign",        "0000-mauvaise-signature");

        ApiResponse<String> result = paiementService.traiterCallbackMonetbil(payload);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Signature");
        verify(paiementRepository, never()).findByReferenceTransactionForUpdate(any());
    }

    @Test
    void callback_pending_ne_modifie_pas_statut() {
        Paiement paiement = paiementEnAttente();

        when(paiementRepository.findByReferenceTransactionForUpdate("REF-001"))
                .thenReturn(Optional.of(paiement));

        ApiResponse<String> result = paiementService.traiterCallbackMonetbil(
                buildSignedPayload("REF-001", "PAY-123", "PENDING"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(paiement.getStatut()).isEqualTo(PaiementStatus.EN_ATTENTE);
        verify(cotisationRepository, never()).save(any());
    }

    @Test
    void callback_cotisation_deja_enregistree_pas_de_doublon() {
        Paiement paiement = paiementEnAttente();
        Cotisation existante = Cotisation.builder().id(99L).statut(PaiementStatus.PAYE).build();

        when(paiementRepository.findByReferenceTransactionForUpdate("REF-001"))
                .thenReturn(Optional.of(paiement));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(anyLong(), anyLong(), anyInt()))
                .thenReturn(Optional.of(existante));
        when(paiementRepository.save(any())).thenReturn(paiement);

        paiementService.traiterCallbackMonetbil(buildSignedPayload("REF-001", "PAY-123", "SUCCESS"));

        verify(cotisationRepository, never()).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Paiement paiementEnAttente() {
        return Paiement.builder()
                .id(50L).membre(membre).montant(new BigDecimal("5000"))
                .operateur(PaiementMode.MTN_MOBILE_MONEY)
                .statut(PaiementStatus.EN_ATTENTE)
                .referenceTransaction("REF-001").build();
    }

    private PaiementMobileMoneyRequest buildRequest(PaiementMode operateur) {
        PaiementMobileMoneyRequest r = new PaiementMobileMoneyRequest();
        r.setMembreId(100L);
        r.setTontineId(10L);
        r.setMontant(new BigDecimal("5000"));
        r.setOperateur(operateur);
        r.setNumeroPaiement("699000001");
        return r;
    }

    /**
     * Construit un payload Monetbil avec la vraie signature HMAC-SHA512.
     * Utilise le même algorithme que PaiementServiceImpl.verifierSignatureMonetbil().
     */
    private Map<String, String> buildSignedPayload(String ref, String paymentRef, String statut) {
        Map<String, String> payload = new HashMap<>();
        payload.put("item_ref",    ref);
        payload.put("payment_ref", paymentRef);
        payload.put("status",      statut);
        payload.put("sign",        computeHmac(payload));
        return payload;
    }

    private String computeHmac(Map<String, String> payload) {
        try {
            StringBuilder sb = new StringBuilder(SERVICE_SECRET);
            payload.entrySet().stream()
                    .filter(e -> !"sign".equals(e.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append(e.getValue()));

            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SERVICE_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erreur calcul HMAC dans le test", e);
        }
    }
}
