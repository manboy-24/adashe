package com.tontine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envoi d'emails en tâche de fond sur le pool notifExecutor.
 * Toute exception est swallowée : l'email ne doit jamais faire échouer le flux principal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailAsyncService {

    private final NotificationService notificationService;

    @Async("notifExecutor")
    public void envoyerEmailAsync(String destinataire, String sujet, String corps) {
        if (destinataire == null || destinataire.isBlank()) return;
        try {
            notificationService.envoyerEmail(destinataire, sujet, corps);
        } catch (Exception e) {
            log.warn("Échec envoi email à {}: {}", destinataire, e.getMessage());
        }
    }
}
