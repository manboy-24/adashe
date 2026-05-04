package com.tontine.service;

import com.google.firebase.messaging.*;
import com.tontine.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envoi asynchrone des push FCM.
 * Séparé de NotificationServiceImpl pour éviter l'auto-invocation (proxy Spring).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushAsyncService {

    private final UtilisateurRepository utilisateurRepository;

    @Async("notifExecutor")
    public void envoyerPushAsync(String fcmToken, String titre, String corps,
                                 String type, Long tontineId) {
        try {
            Message message = Message.builder()
                    .putData("titre",     titre)
                    .putData("message",   corps)
                    .putData("type",      type != null ? type : "GENERAL")
                    .putData("tontineId", tontineId != null ? tontineId.toString() : "")
                    .setToken(fcmToken)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .setContentAvailable(true)
                                    .build())
                            .build())
                    .build();

            String msgId = FirebaseMessaging.getInstance().send(message);
            log.info("[FCM] Push envoyé — type={} token={}… msgId={}",
                    type, fcmToken.substring(0, Math.min(10, fcmToken.length())), msgId);

        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("[FCM] Token révoqué, nettoyage : {}…",
                        fcmToken.substring(0, Math.min(10, fcmToken.length())));
                nettoyerToken(fcmToken);
            } else {
                log.error("[FCM] Erreur : {} — {}", e.getMessagingErrorCode(), e.getMessage());
            }
        } catch (Exception e) {
            log.error("[FCM] Firebase non configuré ou erreur réseau : {}", e.getMessage());
        }
    }

    @Transactional
    protected void nettoyerToken(String fcmToken) {
        utilisateurRepository.findByFcmToken(fcmToken).ifPresent(u -> {
            u.setFcmToken(null);
            utilisateurRepository.save(u);
            log.info("[FCM] Token nettoyé pour id={}", u.getId());
        });
    }
}
