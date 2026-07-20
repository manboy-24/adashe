package com.tontine.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint de diagnostic temporaire : renvoie l'IP sortante (egress) du serveur,
 * telle que la voit un service tiers (ex. Monetbil). Sert à obtenir l'IP à
 * whitelister chez Monetbil. À SUPPRIMER une fois l'IP relevée.
 */
@RestController
@RequestMapping("/diagnostic")
@Slf4j
public class DiagnosticController {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @GetMapping("/ip")
    public Map<String, String> egressIp() {
        Map<String, String> out = new LinkedHashMap<>();
        // Plusieurs sources pour recouper (l'IP doit être la même partout)
        out.put("ipify",     fetch("https://api.ipify.org"));
        out.put("icanhazip", fetch("https://icanhazip.com"));
        out.put("aws",       fetch("https://checkip.amazonaws.com"));
        return out;
    }

    private String fetch(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body().trim();
        } catch (Exception e) {
            return "ERREUR: " + e.getMessage();
        }
    }
}
