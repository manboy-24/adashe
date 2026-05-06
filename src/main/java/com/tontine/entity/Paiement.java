package com.tontine.entity;

import com.tontine.enums.PaiementMode;
import com.tontine.enums.PaiementStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paiements")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cotisation_id")
    private Cotisation cotisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", nullable = false)
    private MembreTontine membre;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String devise = "XAF";   // Franc CFA Cameroun

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaiementMode operateur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaiementStatus statut = PaiementStatus.EN_ATTENTE;

    @Column(unique = true)
    private String referenceTransaction;  // Référence locale (TONTINE-XXXX)

    private String numeroPaieur;          // Numéro de téléphone du payeur

    @Column(length = 500)
    private String messageOperateur;      // Réponse brute de l'opérateur

    // Données gateway (Monetbil) — colonnes DB conservées pour éviter migration
    @Column(name = "cinetpay_transaction_id")
    private String gatewayTransactionId;  // payment_ref Monetbil

    @Column(name = "cinetpay_payment_url")
    private String gatewayPaymentUrl;     // widget_url Monetbil

    /** Montant de l'amende inclus dans ce paiement (0 = paiement dans les délais) */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal montantAmende = BigDecimal.ZERO;

    private LocalDateTime datePaiement;

    // ── Paiement espèces par l'admin ─────────────────────────────────────────
    /** true = l'admin a payé en MoMo pour le compte d'un membre qui a remis du cash */
    @Column(nullable = false)
    @Builder.Default
    private Boolean payePourCompte = false;

    /** Opérateur réel utilisé par l'admin (MTN/Orange) — null si paiement normal */
    @Enumerated(EnumType.STRING)
    private PaiementMode modePaiementReel;

    /** Admin qui a effectué le virement pour le membre */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_payeur_id")
    private Utilisateur adminPayeur;

    /** Cycle ciblé par ce paiement — persiste targetCycle pour que le webhook l'utilise */
    @Column(name = "numero_cycle")
    private Integer numeroCycle;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
