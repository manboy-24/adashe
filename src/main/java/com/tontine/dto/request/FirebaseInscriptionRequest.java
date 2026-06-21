package com.tontine.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class FirebaseInscriptionRequest {

    @NotBlank(message = "Le token Firebase est requis")
    private String idToken;

    @NotBlank(message = "Le PIN est requis")
    @Pattern(regexp = "\\d{4}", message = "Le PIN doit contenir 4 chiffres")
    private String pin;

    @NotBlank(message = "Le nom est requis")
    private String nom;

    @NotBlank(message = "Le prénom est requis")
    private String prenom;

    @Email(message = "Adresse email invalide")
    private String email;
}
