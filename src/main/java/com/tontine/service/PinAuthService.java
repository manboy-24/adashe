package com.tontine.service;

import com.tontine.dto.request.*;
import com.tontine.dto.response.*;

import java.util.List;

public interface PinAuthService {
    ApiResponse<AuthResponse> creerPin(CreationPinRequest request, Long userId);
    ApiResponse<ConnexionPinResponse> connecterAvecPin(ConnexionPinRequest request);
    ApiResponse<ConnexionPinResponse> confirmerNouvelAppareil(ConfirmerNouvelAppareilRequest request);
    ApiResponse<String> demanderResetPin(ResetPinRequest request);
    ApiResponse<AuthResponse> reinitialiserPin(NouveauPinRequest request);
    ApiResponse<String> changerPin(ChangerPinRequest request, Long userId);
    /** Vérifie le PIN de l'utilisateur authentifié sans échanger de tokens. */
    ApiResponse<String> verifierPin(String pin, Long userId);
    List<SessionResponse> listerSessions(Long userId, String currentDeviceId);
    ApiResponse<String> revoquerSession(Long sessionId, Long userId);
    ApiResponse<String> revoquerToutesLesSessions(Long userId, Long exceptSessionId);
}
