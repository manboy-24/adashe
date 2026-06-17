package com.tontine.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Sert /.well-known/assetlinks.json AVANT l'application du context-path /api.
 * Requis pour les Android App Links — Google/Android vérifie ce fichier à la
 * racine du domaine (pas sous /api/).
 *
 * URL finale : https://api.adashcash.com/.well-known/assetlinks.json
 */
@Configuration
public class AssetLinksConfig {

    private static final String ASSET_LINKS_JSON = """
            [{
              "relation": ["delegate_permission/common.handle_all_urls"],
              "target": {
                "namespace": "android_app",
                "package_name": "com.tontineplus.app",
                "sha256_cert_fingerprints": [
                  "BF:D3:43:1C:08:A8:0B:3E:EA:E6:55:05:68:31:99:5F:9C:DF:49:6A:FC:B2:E0:CA:BD:22:5F:AE:52:42:B1:99"
                ]
              }
            }]
            """;

    @Bean
    public FilterRegistrationBean<Filter> assetLinksFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest  httpReq = (HttpServletRequest)  req;
                HttpServletResponse httpRes = (HttpServletResponse) res;

                String uri = httpReq.getRequestURI();
                if ("/.well-known/assetlinks.json".equals(uri)) {
                    httpRes.setContentType("application/json");
                    httpRes.setCharacterEncoding("UTF-8");
                    httpRes.setHeader("Cache-Control", "public, max-age=86400");
                    try (PrintWriter w = httpRes.getWriter()) {
                        w.print(ASSET_LINKS_JSON);
                    }
                    return;
                }
                chain.doFilter(req, res);
            }
        });
        // URL pattern au niveau conteneur — intercepté AVANT le context-path /api
        bean.addUrlPatterns("/.well-known/assetlinks.json");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.setName("assetLinksFilter");
        return bean;
    }
}
