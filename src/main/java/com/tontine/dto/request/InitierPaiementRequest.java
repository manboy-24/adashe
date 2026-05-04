package com.tontine.dto.request;
import com.tontine.enums.OperateurMobileMoney;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class InitierPaiementRequest {
    @NotNull
    private Long cotisationId;

    @NotNull
    private Long tontineId;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(regexp = "^(\\+?237)?6[5-9][0-9]{7}$", message = "Numéro Cameroun invalide (ex: 6XXXXXXXX)")
    private String telephone;

    @NotNull
    private OperateurMobileMoney operateur;

    @NotNull
    @DecimalMin(value = "100", message = "Montant minimum 100 XAF")
    private BigDecimal montant;
}
