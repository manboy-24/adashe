package com.tontine.service;

import com.tontine.dto.request.PaiementMobileMoneyRequest;
import com.tontine.dto.response.*;
import java.util.List;
import java.util.Map;

public interface PaiementService {
    PaiementResponse initierPaiement(PaiementMobileMoneyRequest request, Long userId);
    ApiResponse<String> traiterCallbackMonetbil(Map<String, String> payload);
    List<PaiementResponse> getMesPaiements(Long userId);
}
