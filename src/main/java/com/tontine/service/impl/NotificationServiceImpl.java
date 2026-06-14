package com.tontine.service.impl;

import com.tontine.dto.response.ApiResponse;
import com.tontine.dto.response.NotificationResponse;
import com.tontine.entity.Notification;
import com.tontine.entity.Tontine;
import com.tontine.entity.Utilisateur;
import com.tontine.enums.NotificationType;
import com.tontine.repository.NotificationRepository;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.service.NotificationService;
import com.tontine.service.PushAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository  utilisateurRepository;
    private final JavaMailSender         mailSender;
    private final RestTemplate           restTemplate;
    private final PushAsyncService       pushAsyncService;

    @Value("${sms.provider:console}")
    private String smsProvider;

    // Africa's Talking
    @Value("${africastalking.api-key:}")      private String atApiKey;
    @Value("${africastalking.username:sandbox}") private String atUsername;
    @Value("${africastalking.sender-id:AdasheCash}") private String atSenderId;

    // Twilio
    @Value("${twilio.account-sid:}")  private String twilioAccountSid;
    @Value("${twilio.auth-token:}")   private String twilioAuthToken;   // option A
    @Value("${twilio.api-key:}")      private String twilioApiKey;      // option B
    @Value("${twilio.api-secret:}")   private String twilioApiSecret;   // option B
    @Value("${twilio.from-number:}")  private String twilioFromNumber;

    // ── Canal 1 : Notification en base + Push FCM ─────────────────────────────

    @Override
    public void creerNotification(Utilisateur user, Tontine tontine,
                                  String titre, String message, NotificationType type) {
        // 1. Persister en base (centre de notifications in-app)
        Notification notif = Notification.builder()
                .utilisateur(user)
                .titre(titre)
                .message(message)
                .type(type)
                .referenceId(tontine != null ? tontine.getId() : null)
                .referenceType(tontine != null ? "TONTINE" : null)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notif);

        // 2. Push FCM asynchrone si token présent (n'est pas bloquant pour la requête HTTP)
        if (user.getFcmToken() != null && !user.getFcmToken().isBlank()) {
            pushAsyncService.envoyerPushAsync(
                    user.getFcmToken(), titre, message,
                    type.name(), tontine != null ? tontine.getId() : null
            );
        }
    }

    // ── Canal 2 : Push Firebase Cloud Messaging ───────────────────────────────
    @Override
    public void envoyerPushNotification(String fcmToken, String titre, String corps,
                                        String type, Long tontineId) {
        pushAsyncService.envoyerPushAsync(fcmToken, titre, corps, type, tontineId);
    }

    // ── Canal 3 : SMS (Africa's Talking ou Twilio selon sms.provider) ────────

    @Override
    public void envoyerSms(String telephone, String message) {
        String tel = normaliserTelephone(telephone);
        switch (smsProvider.toLowerCase()) {
            case "twilio"          -> envoyerSmsTwilio(tel, message);
            case "africastalking"  -> envoyerSmsAfricasTalking(tel, message);
            default                -> log.info("[SMS-DEV] → {} : {}", tel, message);
        }
    }

    private void envoyerSmsAfricasTalking(String tel, String message) {
        if (atApiKey == null || atApiKey.isBlank()) {
            log.warn("[SMS-AT] AT_API_KEY non configuré — SMS ignoré pour {}", tel);
            return;
        }
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("username", atUsername);
            params.add("to", tel);
            params.add("message", message);
            if (atSenderId != null && !atSenderId.isBlank()) {
                params.add("from", atSenderId);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("apiKey", atApiKey);
            headers.set("Accept", "application/json");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
            String atUrl = "sandbox".equalsIgnoreCase(atUsername)
                    ? "https://api.sandbox.africastalking.com/version1/messaging"
                    : "https://api.africastalking.com/version1/messaging";
            ResponseEntity<String> response = restTemplate.postForEntity(atUrl, entity, String.class);

            if (response.getStatusCode().value() == 201) {
                log.info("[SMS-AT] Envoyé à {}", tel);
            } else {
                log.warn("[SMS-AT] Réponse inattendue {} pour {} : {}", response.getStatusCode(), tel, response.getBody());
            }
        } catch (Exception e) {
            log.error("[SMS-AT] Erreur pour {} : {}", tel, e.getMessage());
        }
    }

    private void envoyerSmsTwilio(String tel, String message) {
        if (twilioAccountSid == null || twilioAccountSid.isBlank()) {
            log.warn("[SMS-Twilio] TWILIO_ACCOUNT_SID non configuré — SMS ignoré pour {}", tel);
            return;
        }
        try {
            String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("From", twilioFromNumber);
            params.add("To", tel);
            params.add("Body", message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            // API Key (SK...) prioritaire sur Auth Token
            if (twilioApiKey != null && !twilioApiKey.isBlank()) {
                headers.setBasicAuth(twilioApiKey, twilioApiSecret);
            } else {
                headers.setBasicAuth(twilioAccountSid, twilioAuthToken);
            }

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().value() == 201) {
                log.info("[SMS-Twilio] Envoyé à {}", tel);
            } else {
                log.warn("[SMS-Twilio] Réponse inattendue {} pour {} : {}", response.getStatusCode(), tel, response.getBody());
            }
        } catch (Exception e) {
            log.error("[SMS-Twilio] Erreur pour {} : {}", tel, e.getMessage());
        }
    }

    // ── Canal 4 : Email via JavaMailSender (Gmail SMTP) ───────────────────────

    @Override
    public void envoyerEmail(String email, String sujet, String corps) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(email);
            msg.setSubject("[Adashe] " + sujet);
            msg.setText(corps + "\n\n--\nL'équipe Adashe");
            mailSender.send(msg);
            log.info("[EMAIL] Envoyé à {}", email);
        } catch (Exception e) {
            log.error("[EMAIL] Échec envoi à {} : {}", email, e.getMessage());
        }
    }

    // ── Consultation in-app ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMesNotifications(Long userId) {
        return notificationRepository.findByUtilisateurIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(n -> NotificationResponse.builder()
                        .id(n.getId())
                        .titre(n.getTitre())
                        .message(n.getMessage())
                        .type(n.getType())
                        .lue(n.getLue())
                        .referenceId(n.getReferenceId())
                        .referenceType(n.getReferenceType())
                        .createdAt(n.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public ApiResponse<Void> marquerToutesLues(Long userId) {
        notificationRepository.marquerToutesLues(userId);
        return ApiResponse.success(null, "Notifications marquées comme lues");
    }

    @Override
    public long getNombreNonLues(Long userId) {
        return notificationRepository.countByUtilisateurIdAndLueFalse(userId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Normalise un numéro camerounais au format international E.164. */
    private String normaliserTelephone(String telephone) {
        String t = telephone.replaceAll("[^0-9+]", "");
        if (t.startsWith("+"))   return t;
        if (t.startsWith("237")) return "+" + t;
        return "+237" + t;
    }
}
