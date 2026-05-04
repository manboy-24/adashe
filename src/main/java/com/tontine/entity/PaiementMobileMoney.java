package com.tontine.entity;

import com.tontine.enums.OperateurMobileMoney;
import com.tontine.enums.PaiementMobileMoneyStatut;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paiements_mobile_money")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaiementMobileMoney {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cotisation_id")
    private Cotisation cotisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false)
    private String telephone;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String devise = "XAF"; // Franc CFA Cameroun

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperateurMobileMoney operateur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaiementMobileMoneyStatut statut = PaiementMobileMoneyStatut.INITIE;

    // Référence interne unique
    @Column(unique = true, nullable = false)
    private String referenceInterne;

    // Référence retournée par l'opérateur
    private String referenceOperateur;

    // ID de transaction final
    private String transactionId;

    @Column(length = 500)
    private String messageErreur;

    // Nombre de tentatives de vérification du statut
    @Builder.Default
    private Integer nombreVerifications = 0;

    private LocalDateTime dateExpiration;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
