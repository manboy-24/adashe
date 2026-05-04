package com.tontine.filter;

import com.tontine.service.RateLimitService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Limite les appels sur les endpoints sensibles d'authentification.
 * Fonctionne par adresse IP (+ respect du header X-Forwarded-For pour les proxys).
 *
 * Les compteurs sont persistés en base MySQL (table rate_limit) :
 * ils survivent aux redémarrages et fonctionnent sur plusieurs instances.
 *
 * Limites appliquées :
 *  - /auth/inscription    → 5 req / 10 min
 *  - /auth/renvoyer-otp   → 5 req / 10 min
 *  - /auth/pin/connexion  → 10 req / 5 min  (en plus du blocage par compte)
 *  - /paiements/initier   → 5 req / 1 min
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class OtpRateLimitFilter implements Filter {

    private static final long WINDOW_OTP_MS      = 10 * 60 * 1000L;
    private static final long WINDOW_PIN_MS      =  5 * 60 * 1000L;
    private static final long WINDOW_PAIEMENT_MS =  1 * 60 * 1000L;
    private static final int  LIMIT_OTP          = 5;
    private static final int  LIMIT_PIN          = 10;
    private static final int  LIMIT_PAIEMENT     = 5;

    private static final Set<String> OTP_PATHS = Set.of(
            "/auth/inscription",
            "/auth/renvoyer-otp"
    );
    private static final String PIN_PATH      = "/auth/pin/connexion";
    private static final String PAIEMENT_PATH = "/paiements/initier";

    private final RateLimitService rateLimitService;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String uri = request.getRequestURI();
        String ip  = extractIp(request);

        if (OTP_PATHS.stream().anyMatch(uri::endsWith)) {
            if (!rateLimitService.isAllowed(ip, "OTP", LIMIT_OTP, WINDOW_OTP_MS)) {
                log.warn("[RateLimit] IP {} bloquée sur {}", ip, uri);
                sendTooManyRequests(response, "Trop de tentatives. Réessayez dans 10 minutes.");
                return;
            }
        } else if (uri.endsWith(PIN_PATH)) {
            if (!rateLimitService.isAllowed(ip, "PIN", LIMIT_PIN, WINDOW_PIN_MS)) {
                log.warn("[RateLimit] IP {} bloquée sur {}", ip, uri);
                sendTooManyRequests(response, "Trop de tentatives. Réessayez dans 5 minutes.");
                return;
            }
        } else if (uri.endsWith(PAIEMENT_PATH)) {
            if (!rateLimitService.isAllowed(ip, "PAIEMENT", LIMIT_PAIEMENT, WINDOW_PAIEMENT_MS)) {
                log.warn("[RateLimit] IP {} bloquée sur {}", ip, uri);
                sendTooManyRequests(response, "Trop de tentatives de paiement. Réessayez dans 1 minute.");
                return;
            }
        }

        chain.doFilter(req, res);
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
}
