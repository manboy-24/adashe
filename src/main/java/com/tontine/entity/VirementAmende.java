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
 * Trace chaque virement d'amende déclenché vers le compte du développeur.
 * Créé dès qu'un paiement avec amende est confirmé via Monetbil.
 */
@Entity
@Table(name = "virements_amende")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VirementAmende {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paiement_id", nullable = false)
    private Paiement paiement;

    @Column(nullable = false)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaiementMode operateur;

    /** Numéro de téléphone du développeur (destinataire) */
    @Column(nullable = false, length = 20)
    private String numeroBeneficiaire;

    /** Référence du paiement tontine d'origine */
    @Column(nullable = false, length = 50)
    private String referenceTontine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VirementAmendeStatut statut = VirementAmendeStatut.EN_ATTENTE;

    /** Référence retournée par Monetbil lors du transfert */
    @Column(length = 100)
    private String referenceTransfert;

    @Column(length = 500)
    private String messageErreur;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime dateVirement;
}
