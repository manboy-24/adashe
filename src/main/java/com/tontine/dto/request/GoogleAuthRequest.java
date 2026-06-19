package com.tontine.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleAuthRequest {
    @NotBlank(message = "Le token Google est obligatoire")
    private String idToken;
}
