package com.tontine.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Vérifie que toutes les variables d'environnement critiques sont définies
 * avant que l'application accepte du trafic (profil prod uniquement).
 */
@Component
@Profile("prod")
@Slf4j
public class StartupValidator {

    private static final String DEV_JWT_SECRET =
            "Ru9CNN81/7jvrNUvbxo1m53eX/4cmGyQH+Ww+LrjOVCfqthNMIrqxZjcjD7F5qpC6CUBL7DjxcX87ROfXmOhoA==";

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${monetbil.service-key:}")
    private String monetbilServiceKey;

    @Value("${monetbil.service-secret:}")
    private String monetbilServiceSecret;

    @Value("${firebase.credentials-json:}")
    private String firebaseCredentialsJson;

    @Value("${firebase.credentials-path:}")
    private String firebaseCredentialsPath;

    @PostConstruct
    public void validate() {
        List<String> errors = new ArrayList<>();

        // JWT secret — ne doit jamais être la valeur de dev en prod
        if (jwtSecret.isBlank()) {
            errors.add("jwt.secret est vide — définir la variable d'env JWT_SECRET");
        } else if (jwtSecret.equals(DEV_JWT_SECRET)) {
            errors.add("jwt.secret utilise la valeur de dev — générer un secret sécurisé pour la prod");
        } else if (jwtSecret.length() < 64) {
            errors.add("jwt.secret trop court — utiliser au moins 64 caractères");
        }

        // Monetbil
        if (monetbilServiceKey.isBlank()) {
            errors.add("MONETBIL_SERVICE_KEY n'est pas défini");
        }
        if (monetbilServiceSecret.isBlank()) {
            errors.add("MONETBIL_SERVICE_SECRET n'est pas défini");
        }

        // Firebase — l'un des deux doit être présent
        if (firebaseCredentialsJson.isBlank() && firebaseCredentialsPath.isBlank()) {
            errors.add("Firebase : définir FIREBASE_CREDENTIALS_JSON ou FIREBASE_CREDENTIALS");
        }

        if (!errors.isEmpty()) {
            errors.forEach(e -> log.error("[STARTUP] Configuration manquante : {}", e));
            throw new IllegalStateException(
                    "L'application ne peut pas démarrer — " + errors.size() +
                    " variable(s) critique(s) manquante(s). Voir les logs ci-dessus.");
        }

        log.info("[STARTUP] Toutes les variables critiques sont présentes — démarrage autorisé.");
    }
}
