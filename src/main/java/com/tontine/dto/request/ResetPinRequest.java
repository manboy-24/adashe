package com.tontine.dto.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPinRequest {
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Adresse email invalide")
    private String email;
}
