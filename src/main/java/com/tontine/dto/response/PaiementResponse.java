package com.tontine.dto.response;
import com.tontine.enums.PaiementMode;
import com.tontine.enums.PaiementStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaiementResponse {
    private Long id;
    private String referenceTransaction;
    private BigDecimal montant;
    private String devise;
    private PaiementMode operateur;
    private PaiementStatus statut;
    private String numeroPaieur;
    /** URL de paiement (si redirect nécessaire) */
    private String urlPaiement;
    /** Message retourné par l'opérateur */
    private String messageOperateur;
    /**
     * Instructions affichées à l'utilisateur selon l'opérateur :
     * MTN  → "Confirmez sur votre téléphone MTN"
     * Orange → "Composez #150*50# pour confirmer"
     */
    private String instructions;
    private LocalDateTime createdAt;
}
