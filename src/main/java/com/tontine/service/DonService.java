package com.tontine.service;

import com.tontine.dto.request.DonRequest;
import com.tontine.dto.response.ApiResponse;
import com.tontine.dto.response.DonResponse;
import com.tontine.dto.response.DonStatutResponse;

import java.util.Map;

public interface DonService {
    DonResponse initierDon(DonRequest request, Long utilisateurId);
    ApiResponse<String> traiterCallbackDon(Map<String, String> params);
    DonStatutResponse getStatutDon(String reference, Long utilisateurId);
}
