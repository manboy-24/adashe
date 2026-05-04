package com.tontine.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ConfigurerTontineRequest {

    @NotNull
    @DecimalMin("0.5")
    @DecimalMax("10.0")
    private Float commissionPourcent;

    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Numéro MTN invalide")
    private String numeroMtnMomo;

    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Numéro Orange invalide")
    private String numeroOrangeMomo;

}
