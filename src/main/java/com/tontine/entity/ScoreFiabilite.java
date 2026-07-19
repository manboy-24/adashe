package com.tontine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Adashe Score — score de fiabilité communautaire d'un utilisateur.
 *
 * Le score chiffré (0-100) est calculé par des règles déterministes côté Java ;
 * l'explication et la recommandation sont générées par l'IA (Spring AI / Gemini).
 * Cette entité sert de cache persistant : le score n'est recalculé (et l'IA
 * rappelée) que si les données sources ont changé (donneesHash) ou après 24 h.
 */
@Entity
@Table(name = "scores_fiabilite",
       uniqueConstraints = @UniqueConstraint(name = "uk_score_utilisateur", columnNames = "utilisateur_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScoreFiabilite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    /** Score 0-100 calculé par règles pondérées (jamais par l'IA). */
    @Column(nullable = false)
    private Integer score;

    /** ELEVE, MOYEN ou FAIBLE. */
    @Column(nullable = false, length = 20)
    private String niveauConfiance;

    /** Explication en langage simple générée par l'IA. */
    @Column(columnDefinition = "TEXT")
    private String explication;

    /** Recommandation au créateur de la tontine, générée par l'IA. */
    @Column(length = 500)
    private String recommandation;

    /** Empreinte des statistiques sources — invalide le cache quand les données changent. */
    @Column(nullable = false, length = 64)
    private String donneesHash;

    /** Modèle IA utilisé (traçabilité). */
    @Column(length = 50)
    private String modeleIa;

    /** Score prédictif : risque estimé de retard au prochain cycle (FAIBLE | MOYEN | ELEVE). */
    @Column(length = 10)
    private String risqueProchainCycle;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
