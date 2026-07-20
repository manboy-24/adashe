package com.tontine.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tontine.enums.PaiementStatus;
import lombok.*;

import java.math.BigDecimal;

/** Statut d'un don pour le polling côté app (attente de confirmation du débit). */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DonStatutResponse {
    private PaiementStatus statut;      // EN_ATTENTE | PAYE | ANNULE
    private BigDecimal     montant;
    private String         nomComplet;  // nom du donateur, pour l'écran de remerciement
}
