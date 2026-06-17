package com.tontine.controller;

import com.tontine.dto.request.*;
import com.tontine.dto.response.*;
import com.tontine.service.AuthService;
import com.tontine.service.PinAuthService;
import com.tontine.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Inscription, connexion PIN, reset PIN")
public class AuthController {

    private final AuthService authService;
    private final PinAuthService pinAuthService;
    private final SecurityUtil securityUtil;  // Bean injecté, plus statique

    @PostMapping("/inscrire")
    @Operation(summary = "S'inscrire — envoie un OTP par SMS")
    public ResponseEntity<ApiResponse<String>> inscrire(
            @Valid @RequestBody InscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.inscrire(request));
    }

    @PostMapping("/verifier-otp")
    @Operation(summary = "Vérifier le code OTP reçu par SMS")
    public ResponseEntity<ApiResponse<AuthResponse>> verifierOtp(
            @Valid @RequestBody OtpRequest request) {
        return ResponseEntity.ok(authService.verifierOtp(request));
    }

    @PostMapping("/renvoyer-otp")
    @Operation(summary = "Renvoyer un OTP")
    public ResponseEntity<ApiResponse<String>> renvoyerOtp(@RequestParam String telephone) {
        return ResponseEntity.ok(authService.renvoyerOtp(telephone));
    }

    @PostMapping("/pin/verifier")
    @Operation(summary = "Vérifie le PIN de l'utilisateur connecté (sans échange de tokens) — requis avant toute action sensible")
    public ResponseEntity<ApiResponse<String>> verifierPin(
            @Valid @RequestBody VerifierPinRequest request) {
        return ResponseEntity.ok(pinAuthService.verifierPin(request.getPin(), securityUtil.getCurrentUserId()));
    }

    @PostMapping("/pin/creer")
    @Operation(summary = "Créer son PIN 4 chiffres (après OTP vérifié)")
    public ResponseEntity<ApiResponse<AuthResponse>> creerPin(
            @Valid @RequestBody CreationPinRequest request) {
        return ResponseEntity.ok(pinAuthService.creerPin(request, securityUtil.getCurrentUserId()));
    }

    @PostMapping("/pin/connexion")
    @Operation(summary = "Connexion téléphone + PIN")
    public ResponseEntity<ApiResponse<AuthResponse>> connecterAvecPin(
            @Valid @RequestBody ConnexionPinRequest request) {
        return ResponseEntity.ok(pinAuthService.connecterAvecPin(request));
    }

    @PostMapping("/pin/reset/demande")
    @Operation(summary = "Demander la réinitialisation du PIN — envoie un OTP")
    public ResponseEntity<ApiResponse<String>> demanderResetPin(
            @Valid @RequestBody ResetPinRequest request) {
        return ResponseEntity.ok(pinAuthService.demanderResetPin(request));
    }

    @PostMapping("/pin/reset/confirmer")
    @Operation(summary = "Confirmer le nouveau PIN avec le code OTP")
    public ResponseEntity<ApiResponse<AuthResponse>> confirmerResetPin(
            @Valid @RequestBody NouveauPinRequest request) {
        return ResponseEntity.ok(pinAuthService.reinitialiserPin(request));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Rafraîchir le JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.rafraichirToken(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion — invalide le refresh token (JWT requis)")
    public ResponseEntity<ApiResponse<String>> logout() {
        return ResponseEntity.ok(authService.deconnecter(securityUtil.getCurrentUserId()));
    }
}