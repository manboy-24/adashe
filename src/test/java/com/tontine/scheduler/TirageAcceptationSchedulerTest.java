package com.tontine.scheduler;

import com.tontine.entity.*;
import com.tontine.enums.TirageAcceptationStatut;
import com.tontine.enums.TirageType;
import com.tontine.repository.TirageRepository;
import com.tontine.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TirageAcceptationSchedulerTest {

    @Mock private TirageRepository    tirageRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private TirageAcceptationScheduler scheduler;

    private Tirage tirageExpire() {
        Utilisateur createur  = Utilisateur.builder().id(1L).nom("Dupont").prenom("Jean").telephone("699000001").build();
        Tontine tontine       = Tontine.builder().id(10L).nom("Ma Tontine").createur(createur).build();
        Utilisateur gagnant   = Utilisateur.builder().id(2L).nom("Martin").prenom("Alice").telephone("699000002").build();
        MembreTontine membre  = MembreTontine.builder().id(101L).utilisateur(gagnant).tontine(tontine).build();

        return Tirage.builder()
                .id(50L).tontine(tontine).beneficiaire(membre).effectuePar(createur)
                .numeroCycle(1).montantDistribue(new BigDecimal("9900"))
                .commissionPrelevee(new BigDecimal("100"))
                .methodeTirage(TirageType.RANDOM).dateTirage(LocalDate.now())
                .confirme(false)
                .statutAcceptation(TirageAcceptationStatut.EN_ATTENTE)
                .dateExpirationReponse(LocalDateTime.now().minusMinutes(1))
                .build();
    }

    @Test
    void autoAccepterTiragesExpires_passe_le_statut_a_accepte_et_notifie_admin() {
        Tirage tirage = tirageExpire();
        when(tirageRepository.findByStatutAcceptationAndDateExpirationReponseBeforeAndEnLitigeFalse(
                eq(TirageAcceptationStatut.EN_ATTENTE), any(LocalDateTime.class)))
                .thenReturn(List.of(tirage));

        scheduler.autoAccepterTiragesExpires();

        verify(tirageRepository).save(argThat(t -> t.getStatutAcceptation() == TirageAcceptationStatut.ACCEPTE));
        verify(notificationService).creerNotification(
                eq(tirage.getTontine().getCreateur()), eq(tirage.getTontine()), any(), any(), any());
    }

    @Test
    void autoAccepterTiragesExpires_aucun_tirage_expire_ne_fait_rien() {
        when(tirageRepository.findByStatutAcceptationAndDateExpirationReponseBeforeAndEnLitigeFalse(any(), any()))
                .thenReturn(List.of());

        scheduler.autoAccepterTiragesExpires();

        verify(tirageRepository, never()).save(any());
        verify(notificationService, never()).creerNotification(any(), any(), any(), any(), any());
    }
}
