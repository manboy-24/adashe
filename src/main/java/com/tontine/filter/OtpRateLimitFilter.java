package com.tontine.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite les appels sur les endpoints sensibles d'authentification.
 * Fonctionne par adresse IP (+ respect du header X-Forwarded-For pour les proxys).
 *
 * Limites appliquées :
 *  - /auth/inscription       → 5 req / 10 min
 *  - /auth/renvoyer-otp      → 5 req / 10 min
 *  - /auth/pin/connexion     → 10 req / 5 min  (en plus du blocage par compte)
 */
@Component
@Order(1)
@Slf4j
public class OtpRateLimitFilter implements Filter {

    private static final long WINDOW_OTP_MS      = 10 * 60 * 1000L;   // 10 min
    private static final long WINDOW_PIN_MS      =  5 * 60 * 1000L;   //  5 min
    private static final long WINDOW_PAIEMENT_MS =  1 * 60 * 1000L;   //  1 min
    private static final int  LIMIT_OTP          = 5;
    private static final int  LIMIT_PIN          = 10;
    private static final int  LIMIT_PAIEMENT     = 5;

    // Endpoints à protéger (suffixe de l'URI)
    private static final Set<String> OTP_PATHS = Set.of(
            "/auth/inscription",
            "/auth/renvoyer-otp"
    );
    private static final String PIN_PATH      = "/auth/pin/connexion";
    private static final String PAIEMENT_PATH = "/paiements/initier";

    // IP → timestamps des requêtes (buckets séparés par endpoint)
    private final ConcurrentHashMap<String, Deque<Long>> otpBucket      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> pinBucket      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> paiementBucket = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String uri = request.getRequestURI();
        String ip  = extractIp(request);

        if (OTP_PATHS.stream().anyMatch(uri::endsWith)) {
            if (!isAllowed(ip, otpBucket, LIMIT_OTP, WINDOW_OTP_MS)) {
                log.warn("[RateLimit] IP {} bloquée sur {}", ip, uri);
                sendTooManyRequests(response, "Trop de tentatives. Réessayez dans 10 minutes.");
                return;
            }
        } else if (uri.endsWith(PIN_PATH)) {
            if (!isAllowed(ip, pinBucket, LIMIT_PIN, WINDOW_PIN_MS)) {
                log.warn("[RateLimit] IP {} bloquée sur {}", ip, uri);
                sendTooManyRequests(response, "Trop de tentatives. Réessayez dans 5 minutes.");
                return;
            }
        } else if (uri.endsWith(PAIEMENT_PATH)) {
            if (!isAllowed(ip, paiementBucket, LIMIT_PAIEMENT, WINDOW_PAIEMENT_MS)) {
                log.warn("[RateLimit] IP {} bloquée sur {}", ip, uri);
                sendTooManyRequests(response, "Trop de tentatives de paiement. Réessayez dans 1 minute.");
                return;
            }
        }

        chain.doFilter(req, res);
    }

    private boolean isAllowed(String ip, ConcurrentHashMap<String, Deque<Long>> bucket,
                               int limit, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> deque = bucket.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (deque) {
            // Purger les entrées hors fenêtre
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMs) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    private void sendTooManyRequests(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\"}"
        );
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // Nettoyage toutes les heures pour libérer la mémoire des IPs inactives
    @Scheduled(fixedRate = 3_600_000)
    public void purgerBuckets() {
        long threshold = System.currentTimeMillis();
        purger(otpBucket,      threshold, WINDOW_OTP_MS);
        purger(pinBucket,      threshold, WINDOW_PIN_MS);
        purger(paiementBucket, threshold, WINDOW_PAIEMENT_MS);
        log.debug("[RateLimit] Nettoyage buckets — OTP:{} PIN:{} PAIEMENT:{}",
                otpBucket.size(), pinBucket.size(), paiementBucket.size());
    }

    private void purger(ConcurrentHashMap<String, Deque<Long>> bucket, long now, long windowMs) {
        bucket.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                while (!deque.isEmpty() && now - deque.peekFirst() > windowMs) {
                    deque.pollFirst();
                }
                return deque.isEmpty();
            }
        });
    }
}
