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
 * Trace chaque virement de commission déclenché lors de la confirmation d'un tirage.
 * 2 lignes créées par tirage : une pour l'admin (3/4) et une pour Adashe (1/4).
 */
@Entity
@Table(name = "virements_commission")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VirementCommission {

    public enum TypeBeneficiaire { ADMIN, ADASHE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tirage_id", nullable = false)
    private Tirage tirage;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_beneficiaire", nullable = false, length = 10)
    private TypeBeneficiaire typeBeneficiaire;

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
