package com.tontine.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreationPinRequest {
    @NotBlank
    @Pattern(regexp = "\\d{4}", message = "Le PIN doit contenir exactement 4 chiffres")
    private String pin;

    @NotBlank
    private String confirmPin;
}
