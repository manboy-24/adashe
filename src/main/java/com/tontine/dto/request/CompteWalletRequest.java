package com.tontine.dto.request;

import com.tontine.enums.PaiementMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompteWalletRequest {

    @NotNull(message = "L'opérateur est obligatoire")
    private PaiementMode operateur;

    // Numéro Mobile Money (optionnel pour ESPECES)
    private String telephone;

    @NotNull(message = "Le statut actif est obligatoire")
    private Boolean actif;
}
