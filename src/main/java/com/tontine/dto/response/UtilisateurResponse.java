package com.tontine.dto.response;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UtilisateurResponse {
    private Long id;
    private String nom;
    private String prenom;
    private String telephone;
    private String email;
    private String avatarId;
    private Boolean telephoneVerifie;
    /** Indique si le PIN a été configuré (utile côté mobile pour rediriger) */
    private Boolean pinDefini;
    /** true si la version courante du contrat admin a été acceptée (utile côté mobile pour rediriger) */
    private Boolean contratAdminAccepte;
    private LocalDateTime createdAt;
}
