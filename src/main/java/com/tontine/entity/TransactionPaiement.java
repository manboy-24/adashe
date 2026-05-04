package com.tontine.entity;

import com.tontine.enums.OperateurMobileMoney;
import com.tontine.enums.StatutTransaction;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions_paiement")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionPaiement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cotisation_id", nullable = false)
    private Cotisation cotisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payeur_id", nullable = false)
    private Utilisateur payeur;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private OperateurMobileMoney operateur;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    @Builder.Default private StatutTransaction statut = StatutTransaction.EN_ATTENTE;

    @Column(nullable = false) private String numeroPaiement;
    private String referenceOperateur;
    private String transactionId;
    @Column(length = 500) private String messageErreur;
    @Column(columnDefinition = "TEXT") private String webhookPayload;

    @CreatedDate @Column(updatable = false) private LocalDateTime createdAt;
    @LastModifiedDate private LocalDateTime updatedAt;
}
