package com.tontine.service;

import com.tontine.dto.request.*;
import com.tontine.dto.response.*;

public interface PinAuthService {
    ApiResponse<AuthResponse> creerPin(CreationPinRequest request, Long userId);
    ApiResponse<AuthResponse> connecterAvecPin(ConnexionPinRequest request);
    ApiResponse<String> demanderResetPin(ResetPinRequest request);
    ApiResponse<AuthResponse> reinitialiserPin(NouveauPinRequest request);
    ApiResponse<String> changerPin(ChangerPinRequest request, Long userId);
}
