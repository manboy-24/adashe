package com.tontine.dto.response;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MembreResponse {
    private Long membreId;
    private Long utilisateurId;
    private String nom;
    private String prenom;
    private String telephone;
    private String avatarId;
    private String role;           // "ADMIN" pour créateur/admin, "MEMBRE" pour les autres
    private Integer ordreTour;
    private Boolean actif;
    private String statutMembre;            // "ACTIF", "EN_ATTENTE", "RETIRE"
    private Boolean aCagnotteSurCycleActuel;
    private Integer nombreRetards;
    private BigDecimal totalCotise;
    private Boolean aPaye;
    private LocalDateTime dateAdhesion;
}
