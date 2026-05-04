package com.tontine.dto.response;
import com.tontine.enums.OperateurMobileMoney;
import com.tontine.enums.PaiementMobileMoneyStatut;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaiementMobileMoneyResponse {
    private Long id;
    private String referenceInterne;
    private String referenceOperateur;
    private String transactionId;
    private BigDecimal montant;
    private String devise;
    private OperateurMobileMoney operateur;
    private PaiementMobileMoneyStatut statut;
    private String messageErreur;
    private String telephone;
    private LocalDateTime createdAt;
    private LocalDateTime dateExpiration;
    // Message à afficher à l'utilisateur
    private String instructionUtilisateur;
}
