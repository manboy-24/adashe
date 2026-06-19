package com.tontine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Un membre se déclare intéressé pour recevoir la cagnotte du cycle en cours
 * de sa tontine — utile quand le gagnant désigné décline, pour que l'admin
 * choisisse un remplaçant parmi les volontaires plutôt qu'à l'aveugle.
 *
 * Pas de fenêtre de temps : purement social, l'admin tranche quand il veut
 * (voir discussion produit — contrairement à la fenêtre de réponse du gagnant,
 * qui elle est limitée à 15 min).
 */
@Entity
@Table(
    name = "tirage_interets",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tontine_id", "numero_cycle", "membre_id"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TirageInteret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tontine_id", nullable = false)
    private Tontine tontine;

    @Column(name = "numero_cycle", nullable = false)
    private Integer numeroCycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private MembreTontine membre;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
