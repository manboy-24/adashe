package com.tontine.service;

import com.tontine.dto.response.*;
import com.tontine.entity.*;
import com.tontine.enums.NotificationType;
import java.util.List;

public interface NotificationService {

    /**
     * Persiste la notification en base ET envoie un push FCM si l'utilisateur
     * possède un token valide.
     */
    void creerNotification(Utilisateur user, Tontine tontine,
                           String titre, String message, NotificationType type);

    /**
     * Envoie un push FCM data-only (heads-up garanti via onMessageReceived).
     * @param type      valeur du enum NotificationType (ex: "PAIEMENT_RECU")
     * @param tontineId identifiant de la tontine concernée, ou null
     */
    void envoyerPushNotification(String fcmToken, String titre, String corps,
                                 String type, Long tontineId);

    void envoyerSms(String telephone, String message);

    void envoyerEmail(String email, String sujet, String corps);

    List<NotificationResponse> getMesNotifications(Long userId);

    ApiResponse<Void> marquerToutesLues(Long userId);

    long getNombreNonLues(Long userId);
}
