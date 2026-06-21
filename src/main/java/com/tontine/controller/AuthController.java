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

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Inscription, connexion PIN, reset PIN, gestion sessions")
public class AuthController {

    private final AuthService authService;
    private final PinAuthService pinAuthService;
    private final SecurityUtil securityUtil;

    @PostMapping("/inscrire")
    @Operation(summary = "S'inscrire — création de compte directe, PIN à définir ensuite")
    public ResponseEntity<ApiResponse<AuthResponse>> inscrire(
            @Valid @RequestBody InscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.inscrire(request));
    }

    @PostMapping("/firebase/inscrire")
    @Operation(summary = "S'inscrire avec vérification Firebase Phone Auth — PIN inclus dans la requête")
    public ResponseEntity<ApiResponse<AuthResponse>> inscrireFirebase(
            @Valid @RequestBody FirebaseInscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.inscrireAvecFirebase(request));
    }

    @PostMapping("/pin/reset/firebase")
    @Operation(summary = "Réinitialiser le PIN via Firebase Phone Auth ou Email Link")
    public ResponseEntity<ApiResponse<AuthResponse>> resetPinFirebase(
            @Valid @RequestBody FirebasePinResetRequest request) {
        return ResponseEntity.ok(pinAuthService.reinitialiserPinFirebase(request));
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
    @Operation(summary = "Vérifie le PIN de l'utilisateur connecté (sans échange de tokens)")
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
    @Operation(summary = "Connexion téléphone + PIN — renvoie action=CONNECTE ou NOUVEL_APPAREIL_OTP")
    public ResponseEntity<ApiResponse<ConnexionPinResponse>> connecterAvecPin(
            @Valid @RequestBody ConnexionPinRequest request) {
        return ResponseEntity.ok(pinAuthService.connecterAvecPin(request));
    }

    @PostMapping("/sessions/confirmer")
    @Operation(summary = "Confirmer la connexion depuis un nouvel appareil via OTP")
    public ResponseEntity<ApiResponse<ConnexionPinResponse>> confirmerNouvelAppareil(
            @Valid @RequestBody ConfirmerNouvelAppareilRequest request) {
        return ResponseEntity.ok(pinAuthService.confirmerNouvelAppareil(request));
    }

    @GetMapping("/sessions")
    @Operation(summary = "Lister les sessions actives de l'utilisateur connecté")
    public ResponseEntity<List<SessionResponse>> listerSessions(
            @RequestParam(required = false) String deviceId) {
        Long userId = securityUtil.getCurrentUserId();
        return ResponseEntity.ok(pinAuthService.listerSessions(userId, deviceId != null ? deviceId : ""));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "Révoquer une session spécifique")
    public ResponseEntity<ApiResponse<String>> revoquerSession(@PathVariable Long id) {
        return ResponseEntity.ok(pinAuthService.revoquerSession(id, securityUtil.getCurrentUserId()));
    }

    @DeleteMapping("/sessions")
    @Operation(summary = "Révoquer toutes les sessions sauf la courante")
    public ResponseEntity<ApiResponse<String>> revoquerToutesLesSessions(
            @RequestParam(required = false) Long exceptSessionId) {
        return ResponseEntity.ok(
                pinAuthService.revoquerToutesLesSessions(securityUtil.getCurrentUserId(), exceptSessionId));
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

    @PostMapping("/google")
    @Operation(summary = "Connexion / pré-inscription via Google")
    public ResponseEntity<ApiResponse<com.tontine.dto.response.GoogleAuthResponse>> connexionGoogle(
            @Valid @RequestBody com.tontine.dto.request.GoogleAuthRequest request) {
        return ResponseEntity.ok(authService.connexionGoogle(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion — révoque toutes les sessions et le refresh token")
    public ResponseEntity<ApiResponse<String>> logout() {
        return ResponseEntity.ok(authService.deconnecter(securityUtil.getCurrentUserId()));
    }
}
