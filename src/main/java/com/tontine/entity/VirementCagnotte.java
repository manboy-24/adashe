package com.tontine.entity;

import com.tontine.enums.PaiementMode;
import com.tontine.enums.VirementAmendeStatut;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Trace le virement de la cagnotte vers le bénéficiaire après confirmation du tirage.
 * 1 ligne par tirage confirmé.
 */
@Entity
@Table(name = "virements_cagnotte")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VirementCagnotte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tirage_id", nullable = false)
    private Tirage tirage;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaiementMode operateur;

    @Column(nullable = false, length = 20)
    private String numeroBeneficiaire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VirementAmendeStatut statut = VirementAmendeStatut.EN_ATTENTE;

    @Column(length = 100)
    private String referenceTransfert;

    @Column(length = 500)
    private String messageErreur;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime dateVirement;
}
