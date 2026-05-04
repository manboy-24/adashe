package com.tontine.filter;

import com.tontine.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpRateLimitFilterTest {

    @Mock private RateLimitService rateLimitService;

    private OtpRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new OtpRateLimitFilter(rateLimitService);
    }

    // ── Autorisé → la chaîne continue ────────────────────────────────────────

    @Test
    void inscription_autorisee_passe_la_chaine() throws Exception {
        when(rateLimitService.isAllowed(anyString(), eq("OTP"), anyInt(), anyLong()))
                .thenReturn(true);

        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(buildRequest("/api/auth/inscription", "10.0.0.1"), res, chain);

        assertThat(res.getStatus()).isNotEqualTo(429);
        assertThat(chain.getRequest()).isNotNull(); // la chaîne a été poursuivie
    }

    @Test
    void renvoyer_otp_autorise_passe_la_chaine() throws Exception {
        when(rateLimitService.isAllowed(anyString(), eq("OTP"), anyInt(), anyLong()))
                .thenReturn(true);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(buildRequest("/api/auth/renvoyer-otp", "10.0.0.1"), res, new MockFilterChain());

        assertThat(res.getStatus()).isNotEqualTo(429);
    }

    @Test
    void pin_connexion_autorise_passe_la_chaine() throws Exception {
        when(rateLimitService.isAllowed(anyString(), eq("PIN"), anyInt(), anyLong()))
                .thenReturn(true);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(buildRequest("/api/auth/pin/connexion", "10.0.0.1"), res, new MockFilterChain());

        assertThat(res.getStatus()).isNotEqualTo(429);
    }

    // ── Bloqué → 429 ─────────────────────────────────────────────────────────

    @Test
    void inscription_bloquee_retourne_429() throws Exception {
        when(rateLimitService.isAllowed(anyString(), eq("OTP"), anyInt(), anyLong()))
                .thenReturn(false);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(buildRequest("/api/auth/inscription", "10.0.0.2"), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("Trop de tentatives");
    }

    @Test
    void pin_bloque_retourne_429() throws Exception {
        when(rateLimitService.isAllowed(anyString(), eq("PIN"), anyInt(), anyLong()))
                .thenReturn(false);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(buildRequest("/api/auth/pin/connexion", "10.0.0.2"), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    void paiement_bloque_retourne_429() throws Exception {
        when(rateLimitService.isAllowed(anyString(), eq("PAIEMENT"), anyInt(), anyLong()))
                .thenReturn(false);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(buildRequest("/api/paiements/initier", "10.0.0.2"), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("paiement");
    }

    // ── Routes non protégées ──────────────────────────────────────────────────

    @Test
    void route_non_protegee_ne_contacte_pas_le_service() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(buildRequest("/api/tontines", "10.0.0.3"), res, new MockFilterChain());

        assertThat(res.getStatus()).isNotEqualTo(429);
        verify(rateLimitService, never()).isAllowed(any(), any(), anyInt(), anyLong());
    }

    // ── Endpoint correct transmis au service ──────────────────────────────────

    @Test
    void inscription_transmet_endpoint_OTP() throws Exception {
        when(rateLimitService.isAllowed(any(), any(), anyInt(), anyLong())).thenReturn(true);

        filter.doFilter(buildRequest("/api/auth/inscription", "10.0.0.4"),
                new MockHttpServletResponse(), new MockFilterChain());

        verify(rateLimitService).isAllowed(eq("10.0.0.4"), eq("OTP"), anyInt(), anyLong());
    }

    @Test
    void pin_transmet_endpoint_PIN() throws Exception {
        when(rateLimitService.isAllowed(any(), any(), anyInt(), anyLong())).thenReturn(true);

        filter.doFilter(buildRequest("/api/auth/pin/connexion", "10.0.0.5"),
                new MockHttpServletResponse(), new MockFilterChain());

        verify(rateLimitService).isAllowed(eq("10.0.0.5"), eq("PIN"), anyInt(), anyLong());
    }

    // ── X-Forwarded-For ───────────────────────────────────────────────────────

    @Test
    void header_x_forwarded_for_utilise_comme_ip() throws Exception {
        when(rateLimitService.isAllowed(any(), any(), anyInt(), anyLong())).thenReturn(true);

        MockHttpServletRequest req = buildRequest("/api/auth/inscription", "127.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.42, 10.0.0.1");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        verify(rateLimitService).isAllowed(eq("203.0.113.42"), eq("OTP"), anyInt(), anyLong());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private MockHttpServletRequest buildRequest(String uri, String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(remoteAddr);
        return req;
    }
}
