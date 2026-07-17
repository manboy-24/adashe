package com.tontine.dto.response;

import lombok.*;
import java.time.LocalDateTime;

/** Adashe Score — score de fiabilité communautaire d'un membre, avec analyse IA. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ScoreFiabiliteResponse {

    private Long membreId;
    private Long utilisateurId;
    private String nomComplet;
    private String avatarId;

    /** Score 0-100 calculé par règles déterministes. */
    private Integer score;

    /** ELEVE, MOYEN ou FAIBLE. */
    private String niveauConfiance;

    /** Explication en langage simple (générée par IA). */
    private String explication;

    /** Recommandation au créateur (générée par IA). */
    private String recommandation;

    // ── Statistiques brutes (transparence du calcul) ──
    private Integer nombreTontines;
    private Integer cotisationsPayees;
    private Integer cotisationsEnRetard;
    private Integer nombreLitiges;
    private Integer ancienneteMois;

    private LocalDateTime dateCalcul;
}
