package com.tontine.entity;

import com.tontine.enums.FrequenceType;
import com.tontine.enums.TirageType;
import com.tontine.enums.TontineStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tontines")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Tontine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montantContribution;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String devise = "FCFA";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FrequenceType frequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TirageType typeTirage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TontineStatus statut = TontineStatus.EN_ATTENTE;

    private LocalDate dateDebut;
    private LocalDate dateProchainCycle;

    /** Heure du prochain tirage (format "HH:mm"), ex: "18:00" */
    @Column(length = 5)
    @Builder.Default
    private String tirageHeure = "18:00";

    @Column(nullable = false)
    @Builder.Default
    private Integer cycleActuel = 1;

    @Column(nullable = false)
    @Builder.Default
    private Integer nombreMaxMembres = 20;

    // Code unique pour inviter des membres
    @Column(unique = true, nullable = false)
    private String codeInvitation;

    /** Commission de l'admin sur chaque tirage (en %). 0 par défaut — l'admin doit la configurer avant le démarrage. */
    @Column(nullable = false)
    @Builder.Default
    private Float commissionPourcent = 0.0f;

    /** Amende fixe prélevée sur tout membre qui rattrape un cycle non payé (en devise de la tontine). */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal montantAmende = BigDecimal.valueOf(200);

    /** Numéro MTN Mobile Money de l'admin pour les paiements. */
    @Column(length = 20)
    private String numeroMtnMomo;

    /** Numéro Orange Mobile Money de l'admin pour les paiements. */
    @Column(length = 20)
    private String numeroOrangeMomo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "createur_id", nullable = false)
    private Utilisateur createur;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /** Non-null = tontine supprimée (soft-delete). Filtré automatiquement via @SQLRestriction. */
    private LocalDateTime deletedAt;

    // Relations
    @OneToMany(mappedBy = "tontine", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MembreTontine> membres = new ArrayList<>();

    @OneToMany(mappedBy = "tontine", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Cotisation> cotisations = new ArrayList<>();

    @OneToMany(mappedBy = "tontine", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Tirage> tirages = new ArrayList<>();

    // Méthodes utilitaires
    public BigDecimal getMontantCagnotte() {
        return montantContribution.multiply(BigDecimal.valueOf(membres.size()));
    }

    public long getNombreMembresActifs() {
        return membres.stream()
                .filter(m -> m.getStatutMembre() == com.tontine.enums.MembreStatut.ACTIF)
                .count();
    }
}
