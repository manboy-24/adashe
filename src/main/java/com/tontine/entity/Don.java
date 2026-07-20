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
@Table(name = "dons")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Don {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String devise = "XAF";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaiementMode operateur;  // MTN_MOBILE_MONEY | ORANGE_MONEY

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaiementStatus statut = PaiementStatus.EN_ATTENTE;

    @Column(unique = true)
    private String referenceTransaction;  // DON-XXXX

    private String numeroPaieur;          // Numéro Mobile Money du donateur débité

    @Column(length = 500)
    private String messageOperateur;

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;  // payment_ref Monetbil

    @Column(name = "gateway_payment_url")
    private String gatewayPaymentUrl;     // widget_url Monetbil

    /** Tirage qui a motivé ce don — null si don spontané. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tirage_id")
    private Tirage tirage;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
