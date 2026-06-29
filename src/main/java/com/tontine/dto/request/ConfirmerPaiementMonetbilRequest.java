package com.tontine.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmerPaiementMonetbilRequest {
    @NotBlank(message = "La référence du paiement est requise")
    private String itemRef;         // notre référence TONTINE-xxx — paymentResponse.getItem_ref()
    @NotBlank(message = "L'identifiant de transaction Monetbil est requis")
    private String transactionUuid; // paymentResponse.getTransaction_UUID()
}
