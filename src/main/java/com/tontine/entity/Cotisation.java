package com.tontine.entity;

import com.tontine.enums.PaiementStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cotisations")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cotisation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tontine_id", nullable = false)
    private Tontine tontine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private MembreTontine membre;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false)
    private Integer numeroCycle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaiementStatus statut = PaiementStatus.EN_ATTENTE;

    private LocalDate dateEcheance;
    private LocalDate datePaiement;

    // Référence de transaction (CinetPay, Wave, etc.) — unique pour éviter doublons webhook
    @Column(unique = true)
    private String referenceTransaction;
    private String modePaiement;  // "MTN_MOBILE_MONEY", "ORANGE_MONEY", etc.

    /** true si l'admin a payé pour le compte du membre (espèces remises à l'admin). */
    @Column(nullable = false)
    @Builder.Default
    private Boolean payePourCompte = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean estEnRetard = false;

    /** Amende prélevée lors d'un rattrapage (0 si paiement dans les délais). */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal montantAmende = BigDecimal.ZERO;

    @Column(length = 500)
    private String commentaire;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
