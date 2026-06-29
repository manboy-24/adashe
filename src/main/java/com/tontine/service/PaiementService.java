package com.tontine.service;

import com.tontine.dto.request.ConfirmerPaiementMonetbilRequest;
import com.tontine.dto.request.PaiementEspecesRequest;
import com.tontine.dto.request.PaiementMobileMoneyRequest;
import com.tontine.dto.response.*;
import java.util.List;
import java.util.Map;

public interface PaiementService {
    PaiementResponse initierPaiement(PaiementMobileMoneyRequest request, Long userId);
    /** Admin paie en MoMo pour un membre qui a remis du cash — affiché "Espèces" des deux côtés */
    PaiementResponse initierPaiementEspeces(PaiementEspecesRequest request, Long adminId);
    ApiResponse<String> traiterCallbackMonetbil(Map<String, String> payload);
    /** Confirmation appelée par le SDK Android après onPaymentSuccess — vérifie via checkPayment API */
    PaiementResponse confirmerPaiementMonetbil(ConfirmerPaiementMonetbilRequest request, Long userId);
    List<PaiementResponse> getMesPaiements(Long userId);
}
