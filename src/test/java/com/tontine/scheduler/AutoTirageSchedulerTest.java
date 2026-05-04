package com.tontine.scheduler;

import com.tontine.entity.Tontine;
import com.tontine.entity.Utilisateur;
import com.tontine.enums.TirageType;
import com.tontine.enums.TontineStatus;
import com.tontine.repository.TirageRepository;
import com.tontine.repository.TontineRepository;
import com.tontine.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoTirageSchedulerTest {

    @Mock private TontineRepository   tontineRepository;
    @Mock private TirageRepository    tirageRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AutoTirageScheduler scheduler;

    private Utilisateur createur;
    private Tontine     tontine;

    @BeforeEach
    void setUp() {
        createur = Utilisateur.builder()
                .id(1L).nom("Kamga").prenom("Paul").telephone("699000001").build();

        tontine = Tontine.builder()
                .id(10L).nom("Ma Tontine")
                .statut(TontineStatus.ACTIVE)
                .cycleActuel(1)
                .typeTirage(TirageType.RANDOM)
                .montantContribution(new BigDecimal("5000"))
                .devise("XAF")
                .dateProchainCycle(LocalDate.now())
                .createur(createur).build();
    }

    // ── executerTiragesAutomatiques ───────────────────────────────────────────

    @Test
    void aucune_tontine_active_aucune_notification() {
        when(tontineRepository.findActivesParProchainCycle(any())).thenReturn(List.of());

        scheduler.executerTiragesAutomatiques();

        verifyNoInteractions(notificationService, tirageRepository);
    }

    @Test
    void tontine_deja_tiree_ce_cycle_skip() {
        when(tontineRepository.findActivesParProchainCycle(any())).thenReturn(List.of(tontine));
        when(tirageRepository.existsByTontineIdAndNumeroCycle(10L, 1)).thenReturn(true);

        scheduler.executerTiragesAutomatiques();

        verify(notificationService, never()).creerNotification(any(), any(), any(), any(), any());
    }

    @Test
    void tontine_random_non_tiree_envoie_notification_createur() {
        tontine.setTypeTirage(TirageType.RANDOM);
        when(tontineRepository.findActivesParProchainCycle(any())).thenReturn(List.of(tontine));
        when(tirageRepository.existsByTontineIdAndNumeroCycle(10L, 1)).thenReturn(false);

        scheduler.executerTiragesAutomatiques();

        verify(notificationService).creerNotification(
                eq(createur), eq(tontine), contains("tirage"), any(), any());
    }

    @Test
    void tontine_rotatif_non_tiree_envoie_notification_createur() {
        tontine.setTypeTirage(TirageType.ROTATIF);
        when(tontineRepository.findActivesParProchainCycle(any())).thenReturn(List.of(tontine));
        when(tirageRepository.existsByTontineIdAndNumeroCycle(10L, 1)).thenReturn(false);

        scheduler.executerTiragesAutomatiques();

        verify(notificationService).creerNotification(
                eq(createur), eq(tontine), any(), contains("l'ordre des membres"), any());
    }

    @Test
    void tontine_manuel_non_tiree_envoie_notification_createur() {
        tontine.setTypeTirage(TirageType.MANUEL);
        when(tontineRepository.findActivesParProchainCycle(any())).thenReturn(List.of(tontine));
        when(tirageRepository.existsByTontineIdAndNumeroCycle(10L, 1)).thenReturn(false);

        scheduler.executerTiragesAutomatiques();

        verify(notificationService).creerNotification(
                eq(createur), eq(tontine), any(), contains("manuellement"), any());
    }

    @Test
    void plusieurs_tontines_envoient_notification_pour_chacune() {
        Tontine t2 = Tontine.builder()
                .id(20L).nom("Tontine 2").statut(TontineStatus.ACTIVE)
                .cycleActuel(2).typeTirage(TirageType.ROTATIF)
                .createur(createur).dateProchainCycle(LocalDate.now()).build();

        when(tontineRepository.findActivesParProchainCycle(any())).thenReturn(List.of(tontine, t2));
        when(tirageRepository.existsByTontineIdAndNumeroCycle(10L, 1)).thenReturn(false);
        when(tirageRepository.existsByTontineIdAndNumeroCycle(20L, 2)).thenReturn(false);

        scheduler.executerTiragesAutomatiques();

        verify(notificationService, times(2)).creerNotification(any(), any(), any(), any(), any());
    }
}
