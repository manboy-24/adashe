package com.tontine.service;

import com.tontine.dto.request.CotisationRequest;
import com.tontine.dto.request.TirageRequest;
import com.tontine.dto.request.TontineRequest;
import com.tontine.dto.response.CotisationResponse;
import com.tontine.dto.response.TirageResponse;
import com.tontine.dto.response.TontineResponse;
import com.tontine.entity.*;
import com.tontine.enums.*;
import com.tontine.exception.BadRequestException;
import com.tontine.exception.ForbiddenException;
import com.tontine.repository.*;
import com.tontine.service.EmailAsyncService;
import com.tontine.service.NotificationService;
import com.tontine.service.impl.TontineServiceImpl;
import com.tontine.util.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tontine.dto.request.AjoutMembreRequest;
import com.tontine.dto.response.MembreResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TontineServiceTest {

    @Mock private TontineRepository tontineRepository;
    @Mock private MembreTontineRepository membreRepository;
    @Mock private CotisationRepository cotisationRepository;
    @Mock private TirageRepository tirageRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private NotificationService notificationService;
    @Mock private EmailAsyncService emailAsyncService;
    @Mock private SecurityUtil securityUtil;

    @InjectMocks
    private TontineServiceImpl tontineService;

    private Utilisateur createur;
    private Tontine tontine;
    private MembreTontine membreCreateur;

    @BeforeEach
    void setUp() {
        createur = Utilisateur.builder()
                .id(1L).nom("Dupont").prenom("Jean")
                .telephone("699000001").email("jean@test.cm")
                .role(Role.USER).build();

        tontine = Tontine.builder()
                .id(10L).nom("Ma Tontine")
                .montantContribution(new BigDecimal("5000"))
                .devise("XAF")
                .frequence(FrequenceType.MENSUEL)
                .typeTirage(TirageType.RANDOM)
                .statut(TontineStatus.EN_ATTENTE)
                .cycleActuel(1)
                .nombreMaxMembres(20)
                .codeInvitation("ABCD1234")
                .createur(createur)
                .build();

        membreCreateur = MembreTontine.builder()
                .id(100L).utilisateur(createur).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.CREATEUR)
                .statutMembre(MembreStatut.ACTIF).actif(true)
                .nombreRetards(0).build();
    }

    // ── creerTontine ──────────────────────────────────────────────────────────

    @Test
    void creerTontine_succes_sauvegarde_tontine_et_membre_createur() {
        TontineRequest request = new TontineRequest();
        request.setNom("Ma Tontine");
        request.setMontantContribution(new BigDecimal("5000"));
        request.setFrequence(FrequenceType.MENSUEL);
        request.setTypeTirage(TirageType.RANDOM);
        request.setDateDebut(LocalDate.now().plusDays(7));
        request.setNombreMaxMembres(10);
        request.setCotisant(true);

        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(createur));
        when(tontineRepository.findByCodeInvitation(anyString())).thenReturn(Optional.empty());
        when(tontineRepository.save(any(Tontine.class))).thenAnswer(inv -> {
            Tontine t = inv.getArgument(0);
            t.setId(10L);
            return t;
        });
        when(membreRepository.save(any(MembreTontine.class))).thenReturn(membreCreateur);
        when(cotisationRepository.sumMontantPayeByTontineId(anyLong())).thenReturn(null);
        when(membreRepository.findByTontineIdAndStatutMembreNot(anyLong(), any())).thenReturn(List.of());
        when(securityUtil.getCurrentUserId()).thenReturn(1L);

        TontineResponse response = tontineService.creerTontine(request, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getNom()).isEqualTo("Ma Tontine");
        verify(tontineRepository).save(any(Tontine.class));
        verify(membreRepository).save(argThat(m ->
                m.getRoleMembreTontine() == MembreTontineRole.CREATEUR
                && Boolean.TRUE.equals(m.getActif())
                && m.getOrdreTour() == 1
        ));
    }

    @Test
    void creerTontine_createur_non_cotisant_ordre_tour_null() {
        TontineRequest request = new TontineRequest();
        request.setNom("Tontine Observateur");
        request.setMontantContribution(new BigDecimal("10000"));
        request.setFrequence(FrequenceType.HEBDOMADAIRE);
        request.setTypeTirage(TirageType.ROTATIF);
        request.setDateDebut(LocalDate.now());
        request.setCotisant(false);

        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(createur));
        when(tontineRepository.findByCodeInvitation(anyString())).thenReturn(Optional.empty());
        when(tontineRepository.save(any())).thenAnswer(inv -> { Tontine t = inv.getArgument(0); t.setId(10L); return t; });
        when(membreRepository.save(any(MembreTontine.class))).thenReturn(membreCreateur);
        when(cotisationRepository.sumMontantPayeByTontineId(anyLong())).thenReturn(null);
        when(membreRepository.findByTontineIdAndStatutMembreNot(anyLong(), any())).thenReturn(List.of());
        when(securityUtil.getCurrentUserId()).thenReturn(1L);

        tontineService.creerTontine(request, 1L);

        verify(membreRepository).save(argThat(m -> m.getOrdreTour() == null));
    }

    // ── demarrerTontine ───────────────────────────────────────────────────────

    @Test
    void demarrerTontine_succes_passe_a_active() {
        // Tontine avec 2 membres actifs
        MembreTontine m2 = MembreTontine.builder()
                .id(101L).utilisateur(createur).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.ACTIF).actif(true).build();
        tontine.getMembres().add(membreCreateur);
        tontine.getMembres().add(m2);

        when(membreRepository.findByUtilisateurIdAndTontineId(1L, 10L)).thenReturn(Optional.of(membreCreateur));
        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(tontineRepository.save(any())).thenReturn(tontine);
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(membreCreateur, m2));
        when(cotisationRepository.sumMontantPayeByTontineId(anyLong())).thenReturn(null);
        when(membreRepository.findByTontineIdAndStatutMembreNot(anyLong(), any())).thenReturn(List.of(membreCreateur, m2));
        when(securityUtil.getCurrentUserId()).thenReturn(1L);

        TontineResponse response = tontineService.demarrerTontine(10L, 1L);

        assertThat(response).isNotNull();
        assertThat(tontine.getStatut()).isEqualTo(TontineStatus.ACTIVE);
        verify(tontineRepository).save(any());
    }

    @Test
    void demarrerTontine_pas_assez_de_membres_leve_exception() {
        // 1 seul membre actif
        tontine.getMembres().add(membreCreateur);

        when(membreRepository.findByUtilisateurIdAndTontineId(1L, 10L)).thenReturn(Optional.of(membreCreateur));
        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));

        assertThatThrownBy(() -> tontineService.demarrerTontine(10L, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2 membres");
    }

    @Test
    void demarrerTontine_non_admin_leve_exception() {
        MembreTontine membreSimple = MembreTontine.builder()
                .id(200L).utilisateur(createur).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.ACTIF).actif(true).build();

        when(membreRepository.findByUtilisateurIdAndTontineId(2L, 10L)).thenReturn(Optional.of(membreSimple));

        assertThatThrownBy(() -> tontineService.demarrerTontine(10L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void demarrerTontine_deja_active_leve_exception() {
        tontine.setStatut(TontineStatus.ACTIVE);
        tontine.getMembres().add(membreCreateur);
        MembreTontine m2 = MembreTontine.builder().id(101L).utilisateur(createur).tontine(tontine)
                .statutMembre(MembreStatut.ACTIF).actif(true).build();
        tontine.getMembres().add(m2);

        when(membreRepository.findByUtilisateurIdAndTontineId(1L, 10L)).thenReturn(Optional.of(membreCreateur));
        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));

        assertThatThrownBy(() -> tontineService.demarrerTontine(10L, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("déjà démarrée");
    }

    // ── effectuerTirage ───────────────────────────────────────────────────────

    @Test
    void effectuerTirage_random_succes() {
        tontine.setStatut(TontineStatus.ACTIVE);

        Utilisateur benefUtilisateur = Utilisateur.builder()
                .id(2L).nom("Martin").prenom("Alice")
                .telephone("699000002").build();
        MembreTontine beneficiaire = MembreTontine.builder()
                .id(101L).utilisateur(benefUtilisateur).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.ACTIF).actif(true)
                .aCagnotteSurCycleActuel(false).nombreRetards(0).build();

        TirageRequest request = new TirageRequest();

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(tirageRepository.existsByTontineIdAndNumeroCycle(10L, 1)).thenReturn(false);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(createur));
        when(membreRepository.findEligiblesPourTirage(10L)).thenReturn(List.of(beneficiaire));
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(membreCreateur, beneficiaire));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(anyLong(), anyLong(), anyInt()))
                .thenReturn(Optional.empty());
        when(tirageRepository.save(any())).thenAnswer(inv -> {
            Tirage t = inv.getArgument(0);
            t.setId(50L);
            return t;
        });
        when(membreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tontineRepository.save(any())).thenReturn(tontine);

        TirageResponse response = tontineService.effectuerTirage(10L, request, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getBeneficiaireId()).isEqualTo(101L);
        verify(tirageRepository).save(any());
        verify(notificationService, atLeastOnce()).creerNotification(any(), any(), any(), any(), any());
    }

    @Test
    void effectuerTirage_deja_effectue_leve_exception() {
        tontine.setStatut(TontineStatus.ACTIVE);

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(tirageRepository.existsByTontineIdAndNumeroCycle(10L, 1)).thenReturn(true);

        assertThatThrownBy(() -> tontineService.effectuerTirage(10L, new TirageRequest(), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("déjà été effectué");
    }

    @Test
    void effectuerTirage_non_createur_leve_exception() {
        tontine.setStatut(TontineStatus.ACTIVE);

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));

        // userId=2 n'est pas le créateur (créateur est 1)
        assertThatThrownBy(() -> tontineService.effectuerTirage(10L, new TirageRequest(), 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void effectuerTirage_envoie_email_au_beneficiaire() {
        tontine.setStatut(TontineStatus.ACTIVE);

        Utilisateur benefUtilisateur = Utilisateur.builder()
                .id(2L).nom("Martin").prenom("Alice")
                .telephone("699000002").email("alice@test.cm")
                .build();
        MembreTontine beneficiaire = MembreTontine.builder()
                .id(101L).utilisateur(benefUtilisateur).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.ACTIF).actif(true)
                .aCagnotteSurCycleActuel(false).nombreRetards(0).build();

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(tirageRepository.existsByTontineIdAndNumeroCycle(10L, 1)).thenReturn(false);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(createur));
        when(membreRepository.findEligiblesPourTirage(10L)).thenReturn(List.of(beneficiaire));
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(beneficiaire));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(anyLong(), anyLong(), anyInt()))
                .thenReturn(Optional.empty());
        when(tirageRepository.save(any())).thenAnswer(inv -> { Tirage t = inv.getArgument(0); t.setId(50L); return t; });
        when(membreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tontineRepository.save(any())).thenReturn(tontine);

        tontineService.effectuerTirage(10L, new TirageRequest(), 1L);

        verify(emailAsyncService).envoyerEmailAsync(
                eq("alice@test.cm"), contains("cagnotte"), anyString());
    }

    // ── enregistrerCotisation ─────────────────────────────────────────────────

    @Test
    void enregistrerCotisation_succes_par_createur() {
        tontine.setStatut(TontineStatus.ACTIVE);

        Utilisateur membreUser = Utilisateur.builder()
                .id(2L).nom("Talla").prenom("Paul").telephone("699000002").build();
        MembreTontine membre = MembreTontine.builder()
                .id(101L).utilisateur(membreUser).tontine(tontine)
                .statutMembre(MembreStatut.ACTIF).actif(true).build();

        CotisationRequest request = new CotisationRequest();
        request.setTontineId(10L);
        request.setMembreId(101L);
        request.setMontant(new BigDecimal("5000"));
        request.setModePaiement("ESPECES");

        Cotisation savedCotisation = Cotisation.builder()
                .id(200L).tontine(tontine).membre(membre)
                .montant(new BigDecimal("5000")).numeroCycle(1)
                .statut(PaiementStatus.PAYE).datePaiement(LocalDate.now()).build();

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(membreRepository.findById(101L)).thenReturn(Optional.of(membre));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(101L, 10L, 1))
                .thenReturn(Optional.empty());
        when(cotisationRepository.save(any())).thenReturn(savedCotisation);

        CotisationResponse response = tontineService.enregistrerCotisation(request, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getMontant()).isEqualByComparingTo("5000");
        verify(cotisationRepository).save(any());
        verify(notificationService).creerNotification(any(), any(), any(), any(), any());
    }

    @Test
    void enregistrerCotisation_doublon_leve_exception() {
        tontine.setStatut(TontineStatus.ACTIVE);

        Utilisateur membreUser = Utilisateur.builder()
                .id(2L).nom("Talla").prenom("Paul").telephone("699000002").build();
        MembreTontine membre = MembreTontine.builder()
                .id(101L).utilisateur(membreUser).tontine(tontine)
                .statutMembre(MembreStatut.ACTIF).actif(true).build();

        Cotisation existante = Cotisation.builder()
                .id(50L).statut(PaiementStatus.PAYE).build();

        CotisationRequest request = new CotisationRequest();
        request.setTontineId(10L);
        request.setMembreId(101L);
        request.setMontant(new BigDecimal("5000"));

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(membreRepository.findById(101L)).thenReturn(Optional.of(membre));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(101L, 10L, 1))
                .thenReturn(Optional.of(existante));

        assertThatThrownBy(() -> tontineService.enregistrerCotisation(request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("déjà cotisé");
    }

    @Test
    void enregistrerCotisation_montant_insuffisant_leve_exception() {
        tontine.setStatut(TontineStatus.ACTIVE);

        Utilisateur membreUser = Utilisateur.builder()
                .id(2L).nom("Talla").prenom("Paul").telephone("699000002").build();
        MembreTontine membre = MembreTontine.builder()
                .id(101L).utilisateur(membreUser).tontine(tontine)
                .statutMembre(MembreStatut.ACTIF).actif(true).build();

        CotisationRequest request = new CotisationRequest();
        request.setTontineId(10L);
        request.setMembreId(101L);
        request.setMontant(new BigDecimal("1000")); // tontine attend 5000

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(membreRepository.findById(101L)).thenReturn(Optional.of(membre));

        assertThatThrownBy(() -> tontineService.enregistrerCotisation(request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("insuffisant");
    }

    @Test
    void enregistrerCotisation_par_non_membre_non_createur_leve_exception() {
        tontine.setStatut(TontineStatus.ACTIVE);

        // membre appartient à un autre utilisateur (id=3), requête faite par userId=4
        Utilisateur proprietaire = Utilisateur.builder().id(3L).nom("X").prenom("Y").telephone("699000003").build();
        MembreTontine membre = MembreTontine.builder()
                .id(101L).utilisateur(proprietaire).tontine(tontine)
                .statutMembre(MembreStatut.ACTIF).actif(true).build();

        CotisationRequest request = new CotisationRequest();
        request.setTontineId(10L);
        request.setMembreId(101L);
        request.setMontant(new BigDecimal("5000"));

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(membreRepository.findById(101L)).thenReturn(Optional.of(membre));

        // userId=4 : pas créateur (créateur=1) et pas proprio du membre (=3)
        assertThatThrownBy(() -> tontineService.enregistrerCotisation(request, 4L))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── getStatistiques ───────────────────────────────────────────────────────

    @Test
    void getStatistiques_non_createur_leve_exception() {
        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));

        // userId=2 n'est pas le créateur (créateur=1)
        assertThatThrownBy(() -> tontineService.getStatistiques(10L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getStatistiques_createur_retourne_stats() {
        tontine.setStatut(TontineStatus.ACTIVE);
        tontine.getMembres().add(membreCreateur);

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(cotisationRepository.sumMontantPayeByTontineId(10L)).thenReturn(new BigDecimal("10000"));
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(membreCreateur));
        when(cotisationRepository.sumMontantPayeByMembreId(100L)).thenReturn(new BigDecimal("10000"));
        when(cotisationRepository.countPayesByMembreIdAndTontineId(100L, 10L)).thenReturn(2);

        var stats = tontineService.getStatistiques(10L, 1L);

        assertThat(stats.getTotalCollecte()).isEqualByComparingTo("10000");
        assertThat(stats.getNombreMembresActifs()).isEqualTo(1);
        assertThat(stats.getStatsParMembre().get(0).getNombrePaiements()).isEqualTo(2);
    }

    // ── getMesTontines ────────────────────────────────────────────────────────

    @Test
    void getMesTontines_retourne_liste_avec_donnees_batch() {
        tontine.setStatut(TontineStatus.ACTIVE);

        List<Object[]> totauxTontine = new ArrayList<>();
        totauxTontine.add(new Object[]{10L, new BigDecimal("5000")});
        List<Object[]> totauxMembre = new ArrayList<>();
        totauxMembre.add(new Object[]{100L, new BigDecimal("5000")});

        when(tontineRepository.findAllByMembreId(eq(1L), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(tontine)));
        when(cotisationRepository.sumMontantPayeGroupByTontineIds(List.of(10L)))
                .thenReturn(totauxTontine);
        when(membreRepository.findByTontineIdInAndStatutMembreNot(List.of(10L), MembreStatut.RETIRE))
                .thenReturn(List.of(membreCreateur));
        when(cotisationRepository.sumMontantPayeGroupByMembreIds(List.of(100L)))
                .thenReturn(totauxMembre);
        when(cotisationRepository.findMembreIdsAyantPayePourCycle(10L, 1))
                .thenReturn(Set.of(100L));
        when(securityUtil.getCurrentUserId()).thenReturn(1L);

        List<TontineResponse> result = tontineService.getMesTontines(1L,
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalCollecte()).isEqualByComparingTo("5000");
        assertThat(result.get(0).getMembres().get(0).getAPaye()).isTrue();
    }

    @Test
    void getMesTontines_liste_vide_retourne_vide_sans_requetes_supplementaires() {
        when(tontineRepository.findAllByMembreId(eq(1L), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        List<TontineResponse> result = tontineService.getMesTontines(1L,
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertThat(result).isEmpty();
        verifyNoInteractions(cotisationRepository);
    }

    // ── ajouterMembre ─────────────────────────────────────────────────────────

    @Test
    void ajouterMembre_succes_par_telephone() {
        tontine.setStatut(TontineStatus.ACTIVE);

        Utilisateur nouveauUser = Utilisateur.builder()
                .id(2L).nom("Martin").prenom("Claire").telephone("699000002").build();
        AjoutMembreRequest request = new AjoutMembreRequest();
        request.setTelephone("699000002");

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(membreRepository.findByUtilisateurIdAndTontineId(1L, 10L)).thenReturn(Optional.of(membreCreateur));
        when(utilisateurRepository.findByTelephone("699000002")).thenReturn(Optional.of(nouveauUser));
        when(membreRepository.findByUtilisateurIdAndTontineId(2L, 10L)).thenReturn(Optional.empty());
        when(membreRepository.findByTontineId(10L)).thenReturn(List.of(membreCreateur));
        when(membreRepository.save(any())).thenAnswer(inv -> {
            MembreTontine m = inv.getArgument(0);
            m.setId(200L);
            return m;
        });

        MembreResponse result = tontineService.ajouterMembre(10L, request, 1L);

        assertThat(result).isNotNull();
        assertThat(result.getStatutMembre()).isEqualTo("EN_ATTENTE");
        verify(notificationService).creerNotification(eq(nouveauUser), eq(tontine), any(), any(), any());
    }

    @Test
    void ajouterMembre_tontine_pleine_leve_exception() {
        tontine.setNombreMaxMembres(1);
        // 1 membre actif = tontine pleine
        tontine.getMembres().add(membreCreateur);

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(membreRepository.findByUtilisateurIdAndTontineId(1L, 10L)).thenReturn(Optional.of(membreCreateur));

        AjoutMembreRequest request = new AjoutMembreRequest();
        request.setUtilisateurId(2L);

        assertThatThrownBy(() -> tontineService.ajouterMembre(10L, request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void ajouterMembre_non_admin_leve_exception() {
        MembreTontine membreSimple = MembreTontine.builder()
                .id(200L).utilisateur(createur).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.ACTIF).actif(true).build();

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(membreRepository.findByUtilisateurIdAndTontineId(2L, 10L)).thenReturn(Optional.of(membreSimple));

        AjoutMembreRequest request = new AjoutMembreRequest();
        request.setUtilisateurId(3L);

        assertThatThrownBy(() -> tontineService.ajouterMembre(10L, request, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── accepterInvitation / declinerInvitation ───────────────────────────────

    @Test
    void accepterInvitation_met_membre_actif() {
        MembreTontine invite = MembreTontine.builder()
                .id(200L).utilisateur(createur).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.EN_ATTENTE).actif(false).build();

        when(membreRepository.findByUtilisateurIdAndTontineId(2L, 10L)).thenReturn(Optional.of(invite));
        when(membreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tontineService.accepterInvitation(10L, 2L);

        assertThat(invite.getStatutMembre()).isEqualTo(MembreStatut.ACTIF);
        assertThat(invite.getActif()).isTrue();
    }

    @Test
    void accepterInvitation_deja_actif_leve_exception() {
        MembreTontine dejaActif = MembreTontine.builder()
                .id(200L).utilisateur(createur).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.ACTIF).actif(true).build();

        when(membreRepository.findByUtilisateurIdAndTontineId(2L, 10L)).thenReturn(Optional.of(dejaActif));

        assertThatThrownBy(() -> tontineService.accepterInvitation(10L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("déjà membre actif");
    }

    @Test
    void declinerInvitation_met_membre_en_retire() {
        MembreTontine invite = MembreTontine.builder()
                .id(200L).utilisateur(createur).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.EN_ATTENTE).actif(false).build();

        when(membreRepository.findByUtilisateurIdAndTontineId(2L, 10L)).thenReturn(Optional.of(invite));
        when(membreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tontineService.declinerInvitation(10L, 2L);

        assertThat(invite.getStatutMembre()).isEqualTo(MembreStatut.RETIRE);
        assertThat(invite.getActif()).isFalse();
    }

    @Test
    void declinerInvitation_pas_en_attente_leve_exception() {
        MembreTontine actif = MembreTontine.builder()
                .id(200L).utilisateur(createur).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.ACTIF).actif(true).build();

        when(membreRepository.findByUtilisateurIdAndTontineId(2L, 10L)).thenReturn(Optional.of(actif));

        assertThatThrownBy(() -> tontineService.declinerInvitation(10L, 2L))
                .isInstanceOf(BadRequestException.class);
    }

    // ── effectuerTirage ROTATIF / MANUEL ──────────────────────────────────────

    @Test
    void effectuerTirage_rotatif_choisit_membre_ordre_tour_minimum() {
        tontine.setStatut(TontineStatus.ACTIVE);
        tontine.setTypeTirage(TirageType.ROTATIF);

        Utilisateur u2 = Utilisateur.builder().id(2L).nom("Martin").prenom("Alice")
                .telephone("699000002").build();
        MembreTontine m2 = MembreTontine.builder()
                .id(101L).utilisateur(u2).tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.ACTIF).actif(true)
                .ordreTour(1).aCagnotteSurCycleActuel(false).nombreRetards(0).build();
        membreCreateur.setOrdreTour(2);

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(tirageRepository.existsByTontineIdAndNumeroCycle(10L, 1)).thenReturn(false);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(createur));
        when(membreRepository.findEligiblesPourTirage(10L)).thenReturn(List.of(m2, membreCreateur));
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(membreCreateur, m2));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(anyLong(), anyLong(), anyInt()))
                .thenReturn(Optional.empty());
        when(tirageRepository.save(any())).thenAnswer(inv -> { Tirage t = inv.getArgument(0); t.setId(50L); return t; });
        when(membreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tontineRepository.save(any())).thenReturn(tontine);

        TirageResponse response = tontineService.effectuerTirage(10L, new TirageRequest(), 1L);

        // Ordre 1 (m2) doit être sélectionné
        assertThat(response.getBeneficiaireId()).isEqualTo(101L);
    }

    @Test
    void effectuerTirage_manuel_sans_beneficiaire_leve_exception() {
        tontine.setStatut(TontineStatus.ACTIVE);
        tontine.setTypeTirage(TirageType.MANUEL);

        Utilisateur u2 = Utilisateur.builder().id(2L).nom("M").prenom("A").telephone("699000002").build();
        MembreTontine m2 = MembreTontine.builder()
                .id(101L).utilisateur(u2).tontine(tontine)
                .statutMembre(MembreStatut.ACTIF).actif(true)
                .aCagnotteSurCycleActuel(false).nombreRetards(0).build();

        TirageRequest request = new TirageRequest();
        // beneficiaireId absent

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(tirageRepository.existsByTontineIdAndNumeroCycle(10L, 1)).thenReturn(false);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(createur));
        when(membreRepository.findEligiblesPourTirage(10L)).thenReturn(List.of(m2));

        assertThatThrownBy(() -> tontineService.effectuerTirage(10L, request, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bénéficiaire");
    }
}
