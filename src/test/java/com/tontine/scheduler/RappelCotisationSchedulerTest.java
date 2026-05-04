package com.tontine.scheduler;

import com.tontine.entity.*;
import com.tontine.enums.*;
import com.tontine.enums.FrequenceType;
import com.tontine.repository.*;
import com.tontine.service.NotificationService;
import com.tontine.service.SmsAsyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RappelCotisationSchedulerTest {

    @Mock private TontineRepository       tontineRepository;
    @Mock private MembreTontineRepository membreRepository;
    @Mock private CotisationRepository    cotisationRepository;
    @Mock private TirageRepository        tirageRepository;
    @Mock private NotificationService     notificationService;
    @Mock private SmsAsyncService         smsAsyncService;

    @InjectMocks
    private RappelCotisationScheduler scheduler;

    private Utilisateur utilisateur;
    private Utilisateur createur;
    private Tontine     tontine;
    private MembreTontine membre;

    @BeforeEach
    void setUp() {
        createur = Utilisateur.builder()
                .id(1L).nom("Kamga").prenom("Paul").telephone("699000001").build();
        utilisateur = Utilisateur.builder()
                .id(2L).nom("Fotso").prenom("Marc").telephone("677000002").build();

        tontine = Tontine.builder()
                .id(10L).nom("Ma Tontine")
                .statut(TontineStatus.ACTIVE)
                .cycleActuel(1)
                .montantContribution(new BigDecimal("5000"))
                .devise("XAF")
                .frequence(FrequenceType.MENSUEL)
                .dateProchainCycle(LocalDate.now().plusDays(3))
                .createur(createur).build();

        membre = MembreTontine.builder()
                .id(100L).utilisateur(utilisateur).tontine(tontine)
                .actif(true).nombreRetards(0).build();
    }

    // ── rappelTroisJoursAvant ─────────────────────────────────────────────────

    @Test
    void rappelTroisJoursAvant_membre_non_paye_recoit_notification() {
        when(tontineRepository.findActivesParProchainCycle(any())).thenReturn(List.of(tontine));
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(membre));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(100L, 10L, 1))
                .thenReturn(Optional.empty());

        scheduler.rappelTroisJoursAvant();

        verify(notificationService).creerNotification(
                eq(utilisateur), eq(tontine), any(), contains("3 jours"), eq(NotificationType.RAPPEL_COTISATION));
        verify(smsAsyncService).envoyerSmsAsync(eq("677000002"), contains("3 jours"));
    }

    @Test
    void rappelTroisJoursAvant_membre_deja_paye_aucune_notification() {
        Cotisation cotisationPayee = Cotisation.builder().statut(PaiementStatus.PAYE).build();
        when(tontineRepository.findActivesParProchainCycle(any())).thenReturn(List.of(tontine));
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(membre));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(100L, 10L, 1))
                .thenReturn(Optional.of(cotisationPayee));

        scheduler.rappelTroisJoursAvant();

        verify(notificationService, never()).creerNotification(any(), any(), any(), any(), any());
        verify(smsAsyncService, never()).envoyerSmsAsync(any(), any());
    }

    @Test
    void rappelTroisJoursAvant_aucune_tontine_aucune_notification() {
        when(tontineRepository.findActivesParProchainCycle(any())).thenReturn(List.of());

        scheduler.rappelTroisJoursAvant();

        verifyNoInteractions(membreRepository, notificationService, smsAsyncService);
    }

    // ── rappelJourJ ───────────────────────────────────────────────────────────

    @Test
    void rappelJourJ_membre_non_paye_recoit_notification_jour_j() {
        when(tontineRepository.findActivesParProchainCycle(any())).thenReturn(List.of(tontine));
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(membre));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(100L, 10L, 1))
                .thenReturn(Optional.empty());

        scheduler.rappelJourJ();

        verify(notificationService).creerNotification(
                eq(utilisateur), eq(tontine), any(), contains("MTN MoMo"), eq(NotificationType.RAPPEL_COTISATION));
    }

    @Test
    void rappelJourJ_tous_ont_paye_aucune_notification() {
        Cotisation cotisationPayee = Cotisation.builder().statut(PaiementStatus.PAYE).build();
        when(tontineRepository.findActivesParProchainCycle(any())).thenReturn(List.of(tontine));
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(membre));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(100L, 10L, 1))
                .thenReturn(Optional.of(cotisationPayee));

        scheduler.rappelJourJ();

        verify(notificationService, never()).creerNotification(any(), any(), any(), any(), any());
    }

    // ── alerteDeuxTiersDuCycle ────────────────────────────────────────────────

    @Test
    void alerteDeuxTiersDuCycle_aucune_tontine_aucune_action() {
        when(tontineRepository.findActivesAvecCycleDefini()).thenReturn(List.of());

        scheduler.alerteDeuxTiersDuCycle();

        verifyNoInteractions(membreRepository, notificationService, smsAsyncService);
    }

    @Test
    void alerteDeuxTiersDuCycle_tontine_pas_au_bon_jour_skip() {
        // Pour MENSUEL (30j) le 2/3 → Math.max(1,30/3) = 10 jours restants.
        // On fixe dateProchainCycle à aujourd'hui + 15 (pas 10) → skip.
        tontine.setDateProchainCycle(LocalDate.now().plusDays(15));
        when(tontineRepository.findActivesAvecCycleDefini()).thenReturn(List.of(tontine));

        scheduler.alerteDeuxTiersDuCycle();

        verifyNoInteractions(membreRepository, notificationService);
    }

    @Test
    void alerteDeuxTiersDuCycle_tontine_au_bon_jour_membre_non_paye_notifie() {
        // Pour MENSUEL (30j) → joursRestants doit être 10 (30/3)
        tontine.setDateProchainCycle(LocalDate.now().plusDays(10));
        when(tontineRepository.findActivesAvecCycleDefini()).thenReturn(List.of(tontine));
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(membre));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(100L, 10L, 1))
                .thenReturn(Optional.empty());

        scheduler.alerteDeuxTiersDuCycle();

        verify(notificationService).creerNotification(
                eq(utilisateur), eq(tontine), any(), contains("jour(s)"), eq(NotificationType.RAPPEL_COTISATION));
    }

    // ── marquerRetards ────────────────────────────────────────────────────────

    @Test
    void marquerRetards_membre_non_paye_incremente_retard_et_notifie() {
        Tirage tirage = Tirage.builder().id(1L).tontine(tontine).numeroCycle(1)
                .dateTirage(LocalDate.now().minusDays(1)).build();

        when(tirageRepository.findByDateTirage(any())).thenReturn(List.of(tirage));
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(membre));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(100L, 10L, 1))
                .thenReturn(Optional.empty());

        scheduler.marquerRetards();

        verify(membreRepository).save(argThat(m -> m.getNombreRetards() == 1));
        // Notification membre + notification créateur
        verify(notificationService, times(2)).creerNotification(any(), any(), any(), any(), eq(NotificationType.RETARD_PAIEMENT));
    }

    @Test
    void marquerRetards_membre_paye_aucun_retard_marque() {
        Tirage tirage = Tirage.builder().id(1L).tontine(tontine).numeroCycle(1)
                .dateTirage(LocalDate.now().minusDays(1)).build();
        Cotisation cotisationPayee = Cotisation.builder().statut(PaiementStatus.PAYE).build();

        when(tirageRepository.findByDateTirage(any())).thenReturn(List.of(tirage));
        when(membreRepository.findByTontineIdAndActifTrue(10L)).thenReturn(List.of(membre));
        when(cotisationRepository.findByMembreIdAndTontineIdAndNumeroCycle(100L, 10L, 1))
                .thenReturn(Optional.of(cotisationPayee));

        scheduler.marquerRetards();

        verify(membreRepository, never()).save(any());
        verify(notificationService, never()).creerNotification(any(), any(), any(), any(), any());
    }

    @Test
    void marquerRetards_aucun_tirage_hier_aucune_action() {
        when(tirageRepository.findByDateTirage(any())).thenReturn(List.of());

        scheduler.marquerRetards();

        verifyNoInteractions(membreRepository, notificationService);
    }
}
