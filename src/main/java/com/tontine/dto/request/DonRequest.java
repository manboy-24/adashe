package com.tontine.dto.request;

import com.tontine.enums.PaiementMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DonRequest {

    @NotNull(message = "L'opérateur est requis (MTN_MOBILE_MONEY ou ORANGE_MONEY)")
    private PaiementMode operateur;

    @NotNull(message = "Le montant est requis")
    @Min(value = 100, message = "Le montant minimum est de 100 FCFA")
    private Integer montant;
}
