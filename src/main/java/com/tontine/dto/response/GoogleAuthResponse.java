package com.tontine.dto.response;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GoogleAuthResponse {
    private boolean nouvelUtilisateur;
    private String  email;
    private String  nomComplet;
    // Remplis uniquement si l'utilisateur existe déjà
    private String  accessToken;
    private String  refreshToken;
    private String  tokenType;
    private Long    expiresIn;
    private Boolean pinDefini;
    private UtilisateurResponse utilisateur;
}
