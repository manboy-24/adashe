package com.tontine.dto.request;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AjoutMembreRequest {
    // Soit userId (utilisateur existant) soit les infos pour créer un compte
    private Long utilisateurId;
    private String telephone;      // Pour inviter par téléphone
    private Integer ordreTour;     // Pour tirage rotatif
}
