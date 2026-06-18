package com.tontine.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ConfirmerNouvelAppareilRequest {

    @NotBlank(message = "Le téléphone est obligatoire")
    private String telephone;

    @NotBlank(message = "Le PIN est obligatoire")
    @Pattern(regexp = "\\d{4}", message = "Le PIN doit contenir exactement 4 chiffres")
    private String pin;

    @NotBlank(message = "Le code OTP est obligatoire")
    private String otpCode;

    @NotBlank(message = "L'identifiant d'appareil est obligatoire")
    private String deviceId;

    private String deviceName;
}
