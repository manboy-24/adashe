package com.tontine.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ChangerPinRequest {
    @NotBlank
    @Pattern(regexp = "^[0-9]{4}$", message = "L'ancien PIN doit être 4 chiffres")
    private String ancienPin;

    @NotBlank
    @Pattern(regexp = "^[0-9]{4}$", message = "Le nouveau PIN doit être 4 chiffres")
    private String nouveauPin;
}
