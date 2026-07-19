package com.tontine.entity;

import com.tontine.enums.MembreStatut;
import com.tontine.enums.MembreTontineRole;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "membres_tontine",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"utilisateur_id", "tontine_id"}),
           @UniqueConstraint(name = "uk_membre_ordre_tour", columnNames = {"tontine_id", "ordre_tour"})
       })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MembreTontine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tontine_id", nullable = false)
    private Tontine tontine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MembreTontineRole roleMembreTontine = MembreTontineRole.MEMBRE;

    private Integer ordreTour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(255) DEFAULT 'ACTIF'")
    @Builder.Default
    private MembreStatut statutMembre = MembreStatut.EN_ATTENTE;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean actif = false;

    @Column(name = "cagnotte_recue_cycle_actuel", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean aCagnotteSurCycleActuel = false;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    @Builder.Default
    private Integer nombreRetards = 0;

    // Date d'adhésion / dernière invitation
    @CreatedDate
    @Column(name = "date_adhesion")
    private LocalDateTime dateAdhesion;
}
