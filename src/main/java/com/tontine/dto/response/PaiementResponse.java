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
     * Instructions affichées à l'utilisateur : une fenêtre de confirmation
     * s'affiche sur le téléphone, l'utilisateur entre son code secret Mobile Money.
     */
    private String instructions;
    private LocalDateTime createdAt;
    /** true si l'admin a payé en MoMo pour le compte d'un membre (cash) */
    private Boolean payePourCompte;
    /** Frais Monetbil prélevés (commission ~3.8% + TTA 0.2% + 4 FCFA fixe). */
    private BigDecimal fraisGateway;
    /** Montant total débité sur le téléphone du payeur (montant net + fraisGateway). */
    private BigDecimal montantTotal;
}
