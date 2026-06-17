package com.tontine.dto.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SignalerLitigeRequest {
    @NotBlank(message = "Merci d'expliquer le problème signalé")
    private String motif;
}
