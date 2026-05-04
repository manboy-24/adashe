package com.tontine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Délègue les envois SMS vers un thread pool dédié.
 * Évite de bloquer le thread HTTP pendant l'appel à l'API SMS externe (Twilio / AT).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsAsyncService {

    private final NotificationService notificationService;

    @Async("notifExecutor")
    public void envoyerSmsAsync(String telephone, String message) {
        notificationService.envoyerSms(telephone, message);
    }
}
