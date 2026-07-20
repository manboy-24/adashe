package com.tontine.dto.request;

import com.tontine.enums.PaiementMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DonRequest {

    @NotNull(message = "L'opérateur est requis (MTN_MOBILE_MONEY ou ORANGE_MONEY)")
    private PaiementMode operateur;

    @NotNull(message = "Le montant est requis")
    @Min(value = 100, message = "Le montant minimum est de 100 FCFA")
    private Integer montant;

    /** Numéro Mobile Money du donateur, à débiter (fenêtre de confirmation USSD). */
    @NotBlank(message = "Le numéro à débiter est requis")
    private String numeroPaiement;

    /** Optionnel — tirage qui a déclenché l'invitation au don. */
    private Long tirageId;
}
