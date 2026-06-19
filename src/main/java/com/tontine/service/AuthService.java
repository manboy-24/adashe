package com.tontine.service;
import com.tontine.dto.request.*;
import com.tontine.dto.response.*;

public interface AuthService {
    ApiResponse<String> inscrire(InscriptionRequest request);
    ApiResponse<AuthResponse> verifierOtp(OtpRequest request);
    ApiResponse<String> renvoyerOtp(String telephone);
    ApiResponse<AuthResponse> rafraichirToken(String refreshToken);
    ApiResponse<String> deconnecter(Long userId);
    ApiResponse<GoogleAuthResponse> connexionGoogle(GoogleAuthRequest request);
}