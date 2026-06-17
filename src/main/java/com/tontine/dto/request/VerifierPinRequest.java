package com.tontine.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifierPinRequest {
    @NotBlank(message = "Le PIN est requis")
    private String pin;
}
