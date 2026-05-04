package com.tontine.service;

import com.tontine.entity.AuditLog;
import com.tontine.repository.AuditLogRepository;
import com.tontine.service.impl.AuditServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditServiceImpl auditService;

    @Test
    void log_succes_sauvegarde_statut_success() {
        auditService.log(1L, "699000001", "CONNEXION", true, null);

        verify(auditLogRepository).save(argThat(a ->
                a.getUserId().equals(1L)
                && a.getTelephone().equals("699000001")
                && a.getAction().equals("CONNEXION")
                && a.getStatut().equals("SUCCESS")
                && a.getIpAddress() == null
        ));
    }

    @Test
    void log_echec_sauvegarde_statut_failure() {
        auditService.log(1L, "699000001", "CONNEXION", false, "Mauvais PIN");

        verify(auditLogRepository).save(argThat(a ->
                a.getStatut().equals("FAILURE")
                && a.getDetails().equals("Mauvais PIN")
        ));
    }

    @Test
    void log_avec_ip_sauvegarde_ip() {
        auditService.log(1L, "699000001", "INSCRIPTION", true, "192.168.1.1", null);

        verify(auditLogRepository).save(argThat(a ->
                a.getIpAddress().equals("192.168.1.1")
        ));
    }

    @Test
    void log_exception_ne_propage_pas() {
        doThrow(new RuntimeException("DB error")).when(auditLogRepository).save(any());

        // L'audit ne doit jamais faire planter le flux principal
        auditService.log(1L, "699000001", "TEST", true, null);
    }

    @Test
    void log_userId_null_accepte() {
        auditService.log(null, "699000001", "INSCRIPTION", true, null);

        verify(auditLogRepository).save(argThat(a -> a.getUserId() == null));
    }
}
