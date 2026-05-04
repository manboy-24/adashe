package com.tontine.entity;

import com.tontine.enums.TirageType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tirages")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Tirage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tontine_id", nullable = false)
    private Tontine tontine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiaire_id", nullable = false)
    private MembreTontine beneficiaire;

    // Admin qui a effectué le tirage (pour audit)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "effectue_par_id")
    private Utilisateur effectuePar;

    @Column(nullable = false)
    private Integer numeroCycle;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montantDistribue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TirageType methodeTirage;

    private LocalDate dateTirage;

    @Column(nullable = false)
    @Builder.Default
    private Boolean confirme = false;

    @Column(length = 500)
    private String commentaire;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
