package com.tontine.dto.request;

import com.tontine.enums.PaiementMode;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaiementEspecesRequest {

    @NotNull(message = "L'ID membre est obligatoire")
    private Long membreId;

    @NotNull(message = "L'ID tontine est obligatoire")
    private Long tontineId;

    @NotNull @DecimalMin("100")
    private BigDecimal montant;

    /** Opérateur Mobile Money RÉEL utilisé par l'admin (MTN ou Orange) */
    @NotNull(message = "L'opérateur de paiement est obligatoire")
    private PaiementMode operateurReel;

    /** Numéro Mobile Money de l'admin */
    @NotBlank(message = "Le numéro de paiement est obligatoire")
    private String numeroPaiement;

    /** Cycle à rattraper (optionnel, défaut = cycle actuel) */
    private Integer numeroCycle;
}
