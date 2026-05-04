package com.tontine.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class NouveauPinRequest {
    @NotBlank private String telephone;

    @NotBlank(message = "Le code OTP est obligatoire")
    private String codeOtp;

    @NotBlank
    @Pattern(regexp = "\\d{4}", message = "Le nouveau PIN doit contenir 4 chiffres")
    private String nouveauPin;

    @NotBlank
    private String confirmPin;
}
