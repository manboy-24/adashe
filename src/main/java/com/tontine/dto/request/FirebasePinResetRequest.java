package com.tontine.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class FirebasePinResetRequest {

    @NotBlank(message = "Le token Firebase est requis")
    private String idToken;

    @NotBlank(message = "Le nouveau PIN est requis")
    @Pattern(regexp = "\\d{4}", message = "Le PIN doit contenir 4 chiffres")
    private String nouveauPin;
}
