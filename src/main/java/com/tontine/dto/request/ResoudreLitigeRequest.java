package com.tontine.dto.request;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResoudreLitigeRequest {
    /** true = le signalement était justifié (le tirage est invalidé, un remplaçant sera choisi) ; false = rejeté. */
    @NotNull
    private Boolean confirme;
    private String commentaire;
}
