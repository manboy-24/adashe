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

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PushAsyncService       pushAsyncService;

    @Value("${app.mail.from:noreply@adashe.com}") private String mailFrom;
    @Value("${mail.provider:smtp}") private String mailProvider;

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

    // ── Canal 4 : Email (console en dev, SMTP en prod) ───────────────────────

    @Override
    public void envoyerEmail(String email, String sujet, String corps) {
        if ("console".equalsIgnoreCase(mailProvider)) {
            log.info("\n========== [EMAIL-CONSOLE] ==========\nDe      : {}\nÀ       : {}\nSujet   : [Adashe] {}\n\n{}\n=====================================",
                    mailFrom, email, sujet, corps);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(mailFrom);
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

}
