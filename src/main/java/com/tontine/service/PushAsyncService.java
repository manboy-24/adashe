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
            String resolvedType = type != null ? type : "GENERAL";
            Message message = Message.builder()
                    .putData("titre",     titre)
                    .putData("message",   corps)
                    .putData("type",      resolvedType)
                    .putData("tontineId", tontineId != null ? tontineId.toString() : "")
                    // Notification visible même quand l'app est tuée (gérée par le SDK FCM)
                    .setNotification(Notification.builder()
                            .setTitle(titre)
                            .setBody(corps)
                            .build())
                    .setToken(fcmToken)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(channelFor(resolvedType))
                                    .build())
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

    private static String channelFor(String type) {
        return switch (type) {
            case "PAIEMENT_RECU", "RAPPEL_COTISATION", "NOUVEAU_CYCLE", "DON_CONFIRME" -> "tontine_paiement";
            case "TIRAGE_EFFECTUE", "TIRAGE_BENEFICIAIRE"                               -> "tontine_tirage";
            case "RETARD_PAIEMENT", "MEMBRE_BLOQUE"                                    -> "tontine_retard";
            default                                                                     -> "tontine_general";
        };
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
