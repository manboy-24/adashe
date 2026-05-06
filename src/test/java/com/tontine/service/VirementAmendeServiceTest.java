package com.tontine.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tontine.config.DeveloppeurCompteConfig;
import com.tontine.entity.Paiement;
import com.tontine.entity.VirementAmende;
import com.tontine.enums.PaiementMode;
import com.tontine.enums.VirementAmendeStatut;
import com.tontine.exception.BadRequestException;
import com.tontine.exception.ResourceNotFoundException;
import com.tontine.gateway.MonetbilGateway;
import com.tontine.repository.PaiementRepository;
import com.tontine.repository.VirementAmendeRepository;
import com.tontine.service.impl.VirementAmendeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VirementAmendeServiceTest {

    @Mock private VirementAmendeRepository virementAmendeRepository;
    @Mock private PaiementRepository       paiementRepository;
    @Mock private MonetbilGateway          monetbilGateway;
    @Mock private DeveloppeurCompteConfig  developpeurCompte;
    @Mock private VirementAmendeService    selfMock;

    @InjectMocks
    private VirementAmendeService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "monetbilServiceKey",    "svc-key");
        ReflectionTestUtils.setField(service, "monetbilServiceSecret", "svc-secret");
        ReflectionTestUtils.setField(service, "monetbilTransferUrl",   "http://localhost/transfer");
        ReflectionTestUtils.setField(service, "self", selfMock);
    }

    // ── effectuerVirement ─────────────────────────────────────────────────────

    @Test
    void effectuerVirement_montant_zero_ne_fait_rien() {
        service.effectuerVirement(paiement(), BigDecimal.ZERO);
        verifyNoInteractions(virementAmendeRepository, monetbilGateway);
    }

    @Test
    void effectuerVirement_montant_null_ne_fait_rien() {
        service.effectuerVirement(paiement(), null);
        verifyNoInteractions(virementAmendeRepository, monetbilGateway);
    }

    @Test
    void effectuerVirement_succes_marque_statut_succes_et_reference() throws Exception {
        ObjectNode apiResp = JsonNodeFactory.instance.objectNode();
        apiResp.put("success", 1);
        apiResp.put("transaction_id", "TXN-456");

        when(developpeurCompte.getMtnMomo()).thenReturn("237681000000");
        ArgumentCaptor<VirementAmende> captor = ArgumentCaptor.forClass(VirementAmende.class);
        when(virementAmendeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(monetbilGateway.callApi(anyString(), any())).thenReturn(apiResp);

        service.effectuerVirement(paiement(), new BigDecimal("200"));

        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getValue().getStatut()).isEqualTo(VirementAmendeStatut.SUCCES);
        assertThat(captor.getValue().getReferenceTransfert()).isEqualTo("TXN-456");
    }

    @Test
    void effectuerVirement_echec_api_marque_statut_echec() throws Exception {
        ObjectNode apiResp = JsonNodeFactory.instance.objectNode();
        apiResp.put("success", 0);
        apiResp.put("message", "Fonds insuffisants");

        when(developpeurCompte.getMtnMomo()).thenReturn("237681000000");
        ArgumentCaptor<VirementAmende> captor = ArgumentCaptor.forClass(VirementAmende.class);
        when(virementAmendeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(monetbilGateway.callApi(anyString(), any())).thenReturn(apiResp);

        service.effectuerVirement(paiement(), new BigDecimal("200"));

        assertThat(captor.getValue().getStatut()).isEqualTo(VirementAmendeStatut.ECHEC);
        assertThat(captor.getValue().getMessageErreur()).contains("Fonds insuffisants");
    }

    @Test
    void effectuerVirement_exception_reseau_marque_statut_echec() throws Exception {
        when(developpeurCompte.getMtnMomo()).thenReturn("237681000000");
        when(virementAmendeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(monetbilGateway.callApi(anyString(), any())).thenThrow(new RuntimeException("timeout"));

        service.effectuerVirement(paiement(), new BigDecimal("200"));

        verify(virementAmendeRepository, times(2)).save(argThat(v ->
                v.getStatut() == VirementAmendeStatut.ECHEC));
    }

    // ── claimerRetry ──────────────────────────────────────────────────────────

    @Test
    void claimerRetry_statut_echec_bascule_en_attente_et_efface_erreur() {
        VirementAmende virement = VirementAmende.builder()
                .id(10L).statut(VirementAmendeStatut.ECHEC)
                .messageErreur("Erreur précédente").referenceTransfert("OLD-REF").build();

        when(virementAmendeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(virement));
        when(virementAmendeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VirementAmende result = service.claimerRetry(10L);

        assertThat(result.getStatut()).isEqualTo(VirementAmendeStatut.EN_ATTENTE);
        assertThat(result.getMessageErreur()).isNull();
        assertThat(result.getReferenceTransfert()).isNull();
    }

    @Test
    void claimerRetry_statut_succes_leve_BadRequestException() {
        VirementAmende virement = VirementAmende.builder()
                .id(10L).statut(VirementAmendeStatut.SUCCES).build();

        when(virementAmendeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(virement));

        assertThatThrownBy(() -> service.claimerRetry(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("échec");
    }

    @Test
    void claimerRetry_statut_en_attente_leve_BadRequestException() {
        VirementAmende virement = VirementAmende.builder()
                .id(10L).statut(VirementAmendeStatut.EN_ATTENTE).build();

        when(virementAmendeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(virement));

        assertThatThrownBy(() -> service.claimerRetry(10L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void claimerRetry_virement_inexistant_leve_ResourceNotFoundException() {
        when(virementAmendeRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claimerRetry(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── retenterVirement ──────────────────────────────────────────────────────

    @Test
    void retenterVirement_orchestre_claimerRetry_http_enregistrerResultat() throws Exception {
        VirementAmende virement = virementPourRetry();
        VirementAmende resultat = VirementAmende.builder()
                .id(10L).statut(VirementAmendeStatut.SUCCES).build();

        ObjectNode apiResp = JsonNodeFactory.instance.objectNode();
        apiResp.put("success", 1);
        apiResp.put("transaction_id", "TXN-789");

        when(selfMock.claimerRetry(10L)).thenReturn(virement);
        when(monetbilGateway.callApi(anyString(), any())).thenReturn(apiResp);
        when(selfMock.enregistrerResultat(virement)).thenReturn(resultat);

        VirementAmende result = service.retenterVirement(10L);

        assertThat(result.getStatut()).isEqualTo(VirementAmendeStatut.SUCCES);
        verify(selfMock).claimerRetry(10L);
        verify(monetbilGateway).callApi(anyString(), any());
        verify(selfMock).enregistrerResultat(virement);
    }

    @Test
    void retenterVirement_echec_monetbil_passe_quand_meme_a_enregistrerResultat() throws Exception {
        VirementAmende virement = virementPourRetry();
        ObjectNode apiResp = JsonNodeFactory.instance.objectNode();
        apiResp.put("success", 0);
        apiResp.put("message", "Erreur opérateur");

        when(selfMock.claimerRetry(10L)).thenReturn(virement);
        when(monetbilGateway.callApi(anyString(), any())).thenReturn(apiResp);
        when(selfMock.enregistrerResultat(virement)).thenReturn(virement);

        service.retenterVirement(10L);

        verify(selfMock).enregistrerResultat(virement);
        assertThat(virement.getStatut()).isEqualTo(VirementAmendeStatut.ECHEC);
    }

    // ── enregistrerResultat ───────────────────────────────────────────────────

    @Test
    void enregistrerResultat_persiste_et_retourne_virement() {
        VirementAmende virement = VirementAmende.builder()
                .id(10L).statut(VirementAmendeStatut.SUCCES).build();
        when(virementAmendeRepository.save(virement)).thenReturn(virement);

        VirementAmende result = service.enregistrerResultat(virement);

        assertThat(result.getId()).isEqualTo(10L);
        verify(virementAmendeRepository).save(virement);
    }

    // ── effectuerVirementAsyncParId ───────────────────────────────────────────

    @Test
    void effectuerVirementAsyncParId_virement_inexistant_ne_fait_rien() {
        when(virementAmendeRepository.findById(99L)).thenReturn(Optional.empty());

        // ne doit pas lever d'exception
        service.effectuerVirementAsyncParId(99L);

        verify(selfMock, never()).enregistrerResultat(any());
    }

    @Test
    void effectuerVirementAsyncParId_virement_pas_en_attente_ne_fait_rien() {
        VirementAmende virement = VirementAmende.builder().id(1L)
                .statut(VirementAmendeStatut.SUCCES)
                .referenceTontine("TONTINE-ABC")
                .build();
        when(virementAmendeRepository.findById(1L)).thenReturn(Optional.of(virement));

        service.effectuerVirementAsyncParId(1L);

        verify(selfMock, never()).enregistrerResultat(any());
    }

    // ── listerVirements ───────────────────────────────────────────────────────

    @Test
    void listerVirements_avec_statut_delegue_au_repository_filtre() {
        Page<VirementAmende> page = new PageImpl<>(List.of());
        when(virementAmendeRepository.findByStatutOrderByCreatedAtDesc(
                eq(VirementAmendeStatut.ECHEC), any())).thenReturn(page);

        service.listerVirements(VirementAmendeStatut.ECHEC, PageRequest.of(0, 10));

        verify(virementAmendeRepository)
                .findByStatutOrderByCreatedAtDesc(eq(VirementAmendeStatut.ECHEC), any());
        verify(virementAmendeRepository, never()).findAllByOrderByCreatedAtDesc(any());
    }

    @Test
    void listerVirements_sans_statut_retourne_tout() {
        Page<VirementAmende> page = new PageImpl<>(List.of());
        when(virementAmendeRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(page);

        service.listerVirements(null, PageRequest.of(0, 10));

        verify(virementAmendeRepository).findAllByOrderByCreatedAtDesc(any());
        verify(virementAmendeRepository, never())
                .findByStatutOrderByCreatedAtDesc(any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Paiement paiement() {
        return Paiement.builder()
                .id(1L)
                .operateur(PaiementMode.MTN_MOBILE_MONEY)
                .referenceTransaction("TONTINE-ABC123DEF")
                .build();
    }

    private VirementAmende virementPourRetry() {
        return VirementAmende.builder()
                .id(10L)
                .statut(VirementAmendeStatut.EN_ATTENTE)
                .montant(new BigDecimal("200"))
                .numeroBeneficiaire("237681000000")
                .referenceTontine("TONTINE-ABC123")
                .build();
    }
}
