package com.tontine.service;

import com.tontine.dto.response.NotificationResponse;
import com.tontine.entity.Notification;
import com.tontine.entity.Tontine;
import com.tontine.entity.Utilisateur;
import com.tontine.enums.NotificationType;
import com.tontine.repository.NotificationRepository;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UtilisateurRepository  utilisateurRepository;
    @Mock private JavaMailSender         mailSender;
    @Mock private RestTemplate           restTemplate;
    @Mock private PushAsyncService       pushAsyncService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Utilisateur utilisateur;
    private Tontine     tontine;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "smsProvider",  "console");
        ReflectionTestUtils.setField(notificationService, "atApiKey",     "");
        ReflectionTestUtils.setField(notificationService, "atUsername",   "sandbox");
        ReflectionTestUtils.setField(notificationService, "atSenderId",   "Adashe");
        ReflectionTestUtils.setField(notificationService, "twilioAccountSid", "");
        ReflectionTestUtils.setField(notificationService, "twilioAuthToken",  "");
        ReflectionTestUtils.setField(notificationService, "twilioApiKey",     "");
        ReflectionTestUtils.setField(notificationService, "twilioApiSecret",  "");
        ReflectionTestUtils.setField(notificationService, "twilioFromNumber", "");

        utilisateur = Utilisateur.builder()
                .id(1L).nom("Kamga").prenom("Paul").telephone("699000001").build();

        tontine = Tontine.builder()
                .id(10L).nom("Ma Tontine").build();
    }

    // ── creerNotification ─────────────────────────────────────────────────────

    @Test
    void creerNotification_sauvegarde_en_base() {
        notificationService.creerNotification(
                utilisateur, tontine, "Titre", "Message", NotificationType.NOUVEAU_MEMBRE);

        verify(notificationRepository).save(argThat(n ->
                n.getTitre().equals("Titre")
                && n.getMessage().equals("Message")
                && n.getReferenceId().equals(10L)
                && n.getReferenceType().equals("TONTINE")
        ));
    }

    @Test
    void creerNotification_avec_fcm_token_envoie_push() {
        utilisateur.setFcmToken("fcm-token-abc");

        notificationService.creerNotification(
                utilisateur, tontine, "Titre", "Message", NotificationType.TIRAGE_EFFECTUE);

        verify(pushAsyncService).envoyerPushAsync(
                eq("fcm-token-abc"), eq("Titre"), eq("Message"),
                eq("TIRAGE_EFFECTUE"), eq(10L));
    }

    @Test
    void creerNotification_sans_fcm_token_pas_de_push() {
        // fcmToken est null par défaut
        notificationService.creerNotification(
                utilisateur, tontine, "Titre", "Message", NotificationType.NOUVEAU_CYCLE);

        verify(pushAsyncService, never()).envoyerPushAsync(any(), any(), any(), any(), any());
    }

    @Test
    void creerNotification_sans_tontine_reference_null() {
        notificationService.creerNotification(
                utilisateur, null, "Titre", "Message", NotificationType.NOUVEAU_MEMBRE);

        verify(notificationRepository).save(argThat(n ->
                n.getReferenceId() == null && n.getReferenceType() == null));
    }

    // ── envoyerSms ────────────────────────────────────────────────────────────

    @Test
    void envoyerSms_console_ne_contacte_pas_api_externe() {
        notificationService.envoyerSms("699000001", "Test SMS");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void envoyerSms_africastalking_sans_cle_log_et_ignore() {
        ReflectionTestUtils.setField(notificationService, "smsProvider", "africastalking");
        ReflectionTestUtils.setField(notificationService, "atApiKey", "");

        notificationService.envoyerSms("699000001", "Test");

        verifyNoInteractions(restTemplate);
    }

    @Test
    void envoyerSms_twilio_sans_account_sid_log_et_ignore() {
        ReflectionTestUtils.setField(notificationService, "smsProvider", "twilio");
        ReflectionTestUtils.setField(notificationService, "twilioAccountSid", "");

        notificationService.envoyerSms("699000001", "Test");

        verifyNoInteractions(restTemplate);
    }

    // ── envoyerEmail ──────────────────────────────────────────────────────────

    @Test
    void envoyerEmail_envoie_via_mailsender() {
        notificationService.envoyerEmail("test@test.cm", "Sujet", "Corps du message");

        verify(mailSender).send(argThat((SimpleMailMessage m) ->
                m.getSubject() != null && m.getSubject().contains("Sujet")
                && m.getTo() != null && m.getTo()[0].equals("test@test.cm")
        ));
    }

    @Test
    void envoyerEmail_exception_ne_propage_pas() {
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatNoException().isThrownBy(() ->
                notificationService.envoyerEmail("test@test.cm", "Sujet", "Corps"));
    }

    // ── getMesNotifications ───────────────────────────────────────────────────

    @Test
    void getMesNotifications_retourne_liste_mappee() {
        Notification n = Notification.builder()
                .id(1L).titre("T").message("M")
                .type(NotificationType.RAPPEL_COTISATION)
                .lue(false).createdAt(LocalDateTime.now()).build();

        when(notificationRepository.findByUtilisateurIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(n));

        List<NotificationResponse> result = notificationService.getMesNotifications(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitre()).isEqualTo("T");
        assertThat(result.get(0).getLue()).isFalse();
    }

    // ── marquerToutesLues / getNombreNonLues ──────────────────────────────────

    @Test
    void marquerToutesLues_appelle_repository() {
        notificationService.marquerToutesLues(1L);
        verify(notificationRepository).marquerToutesLues(1L);
    }

    @Test
    void getNombreNonLues_retourne_compte() {
        when(notificationRepository.countByUtilisateurIdAndLueFalse(1L)).thenReturn(5L);
        assertThat(notificationService.getNombreNonLues(1L)).isEqualTo(5L);
    }
}
