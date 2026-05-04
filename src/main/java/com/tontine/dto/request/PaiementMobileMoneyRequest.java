package com.tontine.dto.request;
import com.tontine.enums.PaiementMode;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaiementMobileMoneyRequest {
    @NotNull(message = "L'ID membre est obligatoire")
    private Long membreId;

    @NotNull(message = "L'ID tontine est obligatoire")
    private Long tontineId;

    @NotNull @DecimalMin("100")
    private BigDecimal montant;

    /** MTN_MOBILE_MONEY ou ORANGE_MONEY */
    @NotNull(message = "L'operateur est obligatoire")
    private PaiementMode operateur;

    /** Numero qui paie (peut etre different du numero de compte) */
    @NotBlank(message = "Le numero de paiement est obligatoire")
    private String numeroPaiement;

    /**
     * Cycle cible (optionnel).
     * Si renseigné et inférieur au cycle actuel → rattrapage → amende calculée côté serveur.
     */
    private Integer numeroCycle;
}
