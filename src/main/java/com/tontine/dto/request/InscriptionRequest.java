package com.tontine.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class InscriptionRequest {
    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prenom est obligatoire")
    private String prenom;

    @NotBlank(message = "Le telephone est obligatoire")
    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Numero de telephone invalide")
    private String telephone;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Adresse email invalide")
    private String email;
}
