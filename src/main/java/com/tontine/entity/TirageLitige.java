package com.tontine.entity;

import com.tontine.enums.LitigeStatut;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Signalement d'un problème sur un tirage par n'importe quel membre de la
 * tontine (pas seulement le gagnant) — fraude suspectée, erreur de
 * désignation, etc. Suspend le tirage (Tirage.enLitige) jusqu'à ce que
 * l'admin tranche.
 */
@Entity
@Table(name = "tirage_litiges")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TirageLitige {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tirage_id", nullable = false)
    private Tirage tirage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signale_par_id", nullable = false)
    private Utilisateur signalePar;

    @Column(nullable = false, length = 500)
    private String motif;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LitigeStatut statut = LitigeStatut.EN_COURS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolu_par_id")
    private Utilisateur resoluPar;

    private LocalDateTime resoluLe;

    @Column(length = 500)
    private String resolutionCommentaire;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
