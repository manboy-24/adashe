package com.tontine.controller;

import com.tontine.dto.request.FcmTokenRequest;
import com.tontine.dto.response.*;
import com.tontine.entity.Utilisateur;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.service.NotificationService;
import com.tontine.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notifications push et in-app")
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityUtil securityUtil;
    private final UtilisateurRepository utilisateurRepository;

    @GetMapping
    @Operation(summary = "Mes notifications")
    public ResponseEntity<List<NotificationResponse>> getMes() {
        return ResponseEntity.ok(notificationService.getMesNotifications(securityUtil.getCurrentUserId()));
    }

    @PostMapping("/lues")
    @Operation(summary = "Marquer toutes les notifications comme lues")
    public ResponseEntity<ApiResponse<Void>> marquerLues() {
        return ResponseEntity.ok(notificationService.marquerToutesLues(securityUtil.getCurrentUserId()));
    }

    @GetMapping("/non-lues/count")
    @Operation(summary = "Nombre de notifications non lues")
    public ResponseEntity<Long> countNonLues() {
        return ResponseEntity.ok(notificationService.getNombreNonLues(securityUtil.getCurrentUserId()));
    }

    @PostMapping("/fcm-token")
    @Operation(summary = "Enregistrer le token FCM pour les notifications push")
    public ResponseEntity<ApiResponse<String>> enregistrerFcmToken(
            @Valid @RequestBody FcmTokenRequest request) {
        Long userId = securityUtil.getCurrentUserId();
        Utilisateur u = utilisateurRepository.findById(userId).orElseThrow();
        u.setFcmToken(request.getFcmToken());
        utilisateurRepository.save(u);
        return ResponseEntity.ok(ApiResponse.success(null, "Token FCM enregistré"));
    }

    @PostMapping("/test-push")
    @Operation(summary = "Tester les notifications push (envoie uniquement à votre propre token FCM)")
    public ResponseEntity<ApiResponse<String>> testPush() {
        Long userId = securityUtil.getCurrentUserId();
        Utilisateur u = utilisateurRepository.findById(userId).orElseThrow();
        if (u.getFcmToken() == null || u.getFcmToken().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Aucun token FCM enregistré pour votre compte"));
        }
        notificationService.envoyerPushNotification(
                u.getFcmToken(),
                "Test notification 🎉",
                "Votre configuration push fonctionne correctement.",
                "TEST",
                null
        );
        return ResponseEntity.ok(ApiResponse.success(null, "Push envoyé à votre propre token"));
    }
}