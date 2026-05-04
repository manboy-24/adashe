package com.tontine.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CotisationRequest {
    @NotNull private Long membreId;
    @NotNull private Long tontineId;
    @NotNull @DecimalMin("0") private BigDecimal montant;
    private String referenceTransaction;
    private String modePaiement;
    private String commentaire;
    /** Cycle cible — null = cycle actuel, entier = rattrapage d'un cycle passé (espèces uniquement). */
    private Integer numeroCycle;
}
