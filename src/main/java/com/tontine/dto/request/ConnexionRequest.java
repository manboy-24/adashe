package com.tontine.dto.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConnexionRequest {
    @NotBlank private String telephone;
    @NotBlank private String motDePasse;
}
