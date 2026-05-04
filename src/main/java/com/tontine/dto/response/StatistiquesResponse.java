package com.tontine.dto.response;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StatistiquesResponse {
    private Long tontineId;
    private String tontineNom;
    private BigDecimal totalCollecte;
    private BigDecimal totalDistribue;
    private Integer nombreCyclesCompletes;
    private Integer nombreMembresActifs;
    private Integer nombrePaiementsEnRetard;
    private Double tauxPonctualite;
    private List<MembreStatResponse> statsParMembre;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MembreStatResponse {
        private Long membreId;
        private String nomComplet;
        private String avatarId;
        private BigDecimal totalCotise;
        private Integer nombrePaiements;
        private Integer nombreRetards;
        private Boolean aCagnotteSurCycleActuel;
    }
}
