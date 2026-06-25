package com.tontine.service;
import com.tontine.dto.request.*;
import com.tontine.dto.response.*;

public interface AuthService {
    ApiResponse<AuthResponse> inscrire(InscriptionRequest request);
    ApiResponse<AuthResponse> inscrireAvecFirebase(FirebaseInscriptionRequest request);
    ApiResponse<AuthResponse> verifierOtp(OtpRequest request);
    ApiResponse<String> renvoyerOtp(String telephone);
    ApiResponse<AuthResponse> rafraichirToken(String refreshToken);
    ApiResponse<String> deconnecter(Long userId);
    ApiResponse<GoogleAuthResponse> connexionGoogle(GoogleAuthRequest request);
    boolean telephoneExiste(String telephone);
}