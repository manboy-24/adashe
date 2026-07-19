package com.tontine.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ConfigurerTontineRequest {

    // Optionnelle — absente ou 0 = aucune commission prélevée au tirage
    @DecimalMin("0.0")
    @DecimalMax("10.0")
    private Float commissionPourcent;

    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Numéro MTN invalide")
    private String numeroMtnMomo;

    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Numéro Orange invalide")
    private String numeroOrangeMomo;

}
