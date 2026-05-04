package com.tontine.dto.request;
import lombok.Data;

@Data
public class TirageRequest {
    private Long beneficiaireId; // Pour tirage manuel uniquement
    private String commentaire;
}
