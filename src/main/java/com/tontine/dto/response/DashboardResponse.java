package com.tontine.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardResponse {

    private int nombreTontinesActives;
    private int tontinesOuJeSuisCreateur;
    private BigDecimal totalCotise;
    private int mesRetardsTotaux;
    /** Somme des membres actifs dans toutes les tontines dont l'utilisateur est admin. */
    private long totalMembres;

    /** Prochaine échéance de tirage parmi les tontines actives de l'utilisateur. */
    private ProchainTirageInfo prochainTirage;

    /** Tontines du cycle actuel où l'utilisateur n'a pas encore cotisé. */
    private List<CotisationDueInfo> cotisationsEnAttente;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProchainTirageInfo {
        private Long    tontineId;
        private String  tontineNom;
        private LocalDate dateTirage;
        /** montantContribution × nombre de membres actifs. */
        private BigDecimal montantCagnotte;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CotisationDueInfo {
        private Long      tontineId;
        private String    tontineNom;
        private BigDecimal montant;
        private String    devise;
        private LocalDate dateEcheance;
        private int       cycle;
    }
}
