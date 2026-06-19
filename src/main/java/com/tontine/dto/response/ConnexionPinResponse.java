package com.tontine.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * Réponse à POST /auth/pin/connexion et /auth/sessions/confirmer.
 * {@code action = "CONNECTE"}          → auth réussie, tokens présents.
 * {@code action = "NOUVEL_APPAREIL_OTP"} → appareil inconnu, OTP envoyé, otpDestination indique où.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnexionPinResponse {

    private String action;

    // Présents uniquement si action = CONNECTE
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long   expiresIn;
    private Boolean pinDefini;
    private UtilisateurResponse utilisateur;

    // Présent uniquement si action = NOUVEL_APPAREIL_OTP
    private String otpDestination;
}
