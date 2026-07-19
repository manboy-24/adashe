package com.tontine.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Configuration
@Slf4j
public class FirebaseConfig {

    // En production : FIREBASE_CREDENTIALS_JSON = base64 du JSON service account
    // En dev       : laisser vide, le fichier classpath:firebase-service-account.json est utilisé
    @Value("${firebase.credentials-json:}")
    private String credentialsJsonBase64;

    @Value("${firebase.credentials-path}")
    private Resource credentialsResource;

    @Bean
    public FirebaseApp firebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        try {
            InputStream is = resolveCredentials();
            if (is == null) {
                log.warn("Firebase désactivé — aucune credential configurée (push notifications off)");
                return null;
            }
            try (is) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(is);
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();
                log.info("Firebase initialisé");
                return FirebaseApp.initializeApp(options);
            }
        } catch (Exception e) {
            // RuntimeException (ex: IllegalArgumentException sur Base64 invalide) ou IOException
            log.warn("Erreur init Firebase : {} — push désactivé", e.getMessage());
            return null;
        }
    }

    // Priorité : variable d'env base64 (prod) > fichier classpath (dev)
    private InputStream resolveCredentials() {
        if (credentialsJsonBase64 != null && !credentialsJsonBase64.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(credentialsJsonBase64.trim());
                return new ByteArrayInputStream(decoded);
            } catch (IllegalArgumentException e) {
                log.warn("FIREBASE_CREDENTIALS_JSON invalide (Base64 corrompu : {}) — push désactivé", e.getMessage());
                return null;
            }
        }
        try {
            return credentialsResource.getInputStream();
        } catch (IOException e) {
            return null;
        }
    }
}
