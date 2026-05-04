package com.tontine.service;

import com.tontine.repository.RateLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RateLimitRepository repo;

    /**
     * Incrémente le compteur de l'IP pour cet endpoint et vérifie la limite.
     *
     * @param ip        adresse IP du client
     * @param endpoint  identifiant court de l'endpoint ("OTP", "PIN", "PAIEMENT")
     * @param limit     nombre maximum de requêtes autorisées dans la fenêtre
     * @param windowMs  durée de la fenêtre en millisecondes
     * @return true si la requête est autorisée, false si la limite est dépassée
     */
    @Transactional
    public boolean isAllowed(String ip, String endpoint, int limit, long windowMs) {
        repo.upsert(ip, endpoint, windowMs / 1000);
        int hits = repo.findHits(ip, endpoint).orElse(0);
        return hits <= limit;
    }

    // Nettoyage toutes les heures : supprime les fenêtres expirées depuis plus d'1 heure
    // (la fenêtre max est 10 min → tout row > 1 h est définitivement périmé)
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void purgerExpires() {
        LocalDateTime seuil = LocalDateTime.now().minusHours(1);
        repo.deleteExpiredBefore(seuil);
        log.debug("[RateLimit] Nettoyage BDD — lignes antérieures à {}", seuil);
    }
}
