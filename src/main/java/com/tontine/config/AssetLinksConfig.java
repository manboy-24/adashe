package com.tontine.config;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletException;
import java.io.IOException;

/**
 * Sert /.well-known/assetlinks.json à la racine du domaine via un Engine Valve Tomcat,
 * AVANT l'application du context-path /api — requis pour Android App Links.
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
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> assetLinksCustomizer() {
        return factory -> factory.addEngineValves(new ValveBase() {
            @Override
            public void invoke(Request request, Response response) throws IOException, ServletException {
                if ("/.well-known/assetlinks.json".equals(request.getRequestURI())) {
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.addHeader("Cache-Control", "public, max-age=86400");
                    response.getWriter().print(ASSET_LINKS_JSON);
                    return;
                }
                getNext().invoke(request, response);
            }
        });
    }
}
