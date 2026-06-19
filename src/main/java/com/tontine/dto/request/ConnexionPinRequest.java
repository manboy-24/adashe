package com.tontine.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ConnexionPinRequest {
    @NotBlank(message = "Le telephone est obligatoire")
    private String telephone;

    @NotBlank(message = "Le PIN est obligatoire")
    @Pattern(regexp = "\\d{4}", message = "Le PIN doit contenir exactement 4 chiffres")
    private String pin;

    /** Identifiant stable de l'appareil (Android ID). Optionnel pour la backward-compat. */
    private String deviceId;

    /** Nom lisible de l'appareil (ex : "Samsung Galaxy S21"). */
    private String deviceName;
}
