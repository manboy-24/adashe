package com.tontine.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OtpRateLimitFilterTest {

    private OtpRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new OtpRateLimitFilter();
    }

    @Test
    void inscription_dans_la_limite_passe() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = buildRequest("/api/auth/inscription", "10.0.0.1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(req, res, chain);

            assertThat(res.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void inscription_depasse_limite_retourne_429() throws Exception {
        String ip = "10.0.0.2";
        // Envoyer 5 requêtes (limite max)
        for (int i = 0; i < 5; i++) {
            filter.doFilter(buildRequest("/api/auth/inscription", ip),
                    new MockHttpServletResponse(), new MockFilterChain());
        }

        // La 6e dépasse la limite
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(buildRequest("/api/auth/inscription", ip), res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentAsString()).contains("Trop de tentatives");
    }

    @Test
    void ips_differentes_ne_se_bloquent_pas_mutuellement() throws Exception {
        // IP A dépasse la limite
        for (int i = 0; i < 6; i++) {
            filter.doFilter(buildRequest("/api/auth/inscription", "10.0.0.3"),
                    new MockHttpServletResponse(), new MockFilterChain());
        }

        // IP B n'est pas affectée
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(buildRequest("/api/auth/inscription", "10.0.0.4"), res, new MockFilterChain());

        assertThat(res.getStatus()).isNotEqualTo(429);
    }

    @Test
    void route_non_limitee_passe_toujours() throws Exception {
        String ip = "10.0.0.5";
        // 20 appels sur une route non limitée
        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(buildRequest("/api/tontines", ip), res, new MockFilterChain());
            assertThat(res.getStatus()).isNotEqualTo(429);
        }
    }

    @Test
    void pin_connexion_limite_plus_haute() throws Exception {
        String ip = "10.0.0.6";
        // 10 requêtes (limite PIN) → toutes passent
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(buildRequest("/api/auth/pin/connexion", ip), res, new MockFilterChain());
            assertThat(res.getStatus()).isNotEqualTo(429);
        }

        // La 11e est bloquée
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(buildRequest("/api/auth/pin/connexion", ip), res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    void header_x_forwarded_for_utilise_pour_ip() throws Exception {
        String ip = "203.0.113.42";
        // Dépasser la limite avec l'IP du header
        for (int i = 0; i < 6; i++) {
            MockHttpServletRequest req = buildRequest("/api/auth/inscription", "127.0.0.1");
            req.addHeader("X-Forwarded-For", ip + ", 10.0.0.1");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletRequest req = buildRequest("/api/auth/inscription", "127.0.0.1");
        req.addHeader("X-Forwarded-For", ip + ", 10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(429);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private MockHttpServletRequest buildRequest(String uri, String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(remoteAddr);
        return req;
    }
}
