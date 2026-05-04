package com.tontine.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;

/**
 * Requête de modification d'une tontine.
 *
 * Champs toujours modifiables : nom, description, dateProchainCycle, tirageHeure.
 * Champs verrouillés après démarrage : montantContribution, frequence, typeTirage.
 */
@Data
public class ModifierTontineRequest {

    @NotBlank
    private String nom;

    private String description;

    /** Nouvelle date du prochain tirage (optionnel). */
    private LocalDate dateProchainCycle;

    /** Heure du tirage au format "HH:mm" (optionnel, ex: "18:00"). */
    private String tirageHeure;
}
