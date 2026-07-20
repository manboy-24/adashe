package com.tontine.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tontine.enums.PaiementMode;
import com.tontine.enums.PaiementStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DonResponse {
    private Long         id;
    private String       referenceTransaction;
    private BigDecimal   montant;
    private String       devise;
    private PaiementMode operateur;
    private PaiementStatus statut;
    private String       urlPaiement;      // widget_url Monetbil si disponible
    private String       codeUssd;         // code à composer (*126# MTN, #150*50# Orange) — flow USSD
    private String       messageOperateur;
    private String       instructions;
    private LocalDateTime createdAt;
}
