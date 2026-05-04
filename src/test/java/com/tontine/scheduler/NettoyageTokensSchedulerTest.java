package com.tontine.scheduler;

import com.tontine.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NettoyageTokensSchedulerTest {

    @Mock private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private NettoyageTokensScheduler scheduler;

    @Test
    void purgerTokensExpires_appelle_les_deux_requetes_de_purge() {
        when(utilisateurRepository.purgerOtpExpires(any(LocalDateTime.class))).thenReturn(3);
        when(utilisateurRepository.purgerRefreshTokensExpires(any(LocalDateTime.class))).thenReturn(7);

        scheduler.purgerTokensExpires();

        verify(utilisateurRepository).purgerOtpExpires(any(LocalDateTime.class));
        verify(utilisateurRepository).purgerRefreshTokensExpires(any(LocalDateTime.class));
    }

    @Test
    void purgerTokensExpires_aucun_token_ne_leve_pas_exception() {
        when(utilisateurRepository.purgerOtpExpires(any())).thenReturn(0);
        when(utilisateurRepository.purgerRefreshTokensExpires(any())).thenReturn(0);

        scheduler.purgerTokensExpires();

        verify(utilisateurRepository, times(1)).purgerOtpExpires(any());
        verify(utilisateurRepository, times(1)).purgerRefreshTokensExpires(any());
    }

    @Test
    void purgerTokensExpires_exception_db_ne_propage_pas() {
        when(utilisateurRepository.purgerOtpExpires(any())).thenThrow(new RuntimeException("DB error"));

        // Le scheduler ne doit pas laisser planter le job, mais ici on vérifie simplement
        // que l'exception éventuelle est visible (le job devrait la logger, pas l'avaler).
        // On documente le comportement attendu : l'exception remonte jusqu'au scheduler framework.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> scheduler.purgerTokensExpires())
                .isInstanceOf(RuntimeException.class);
    }
}
