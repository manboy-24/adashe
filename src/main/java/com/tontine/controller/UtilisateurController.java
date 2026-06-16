package com.tontine.controller;

import com.tontine.dto.request.FcmTokenRequest;
import com.tontine.dto.request.UpdateProfilRequest;
import com.tontine.dto.response.ApiResponse;
import com.tontine.dto.response.UtilisateurResponse;
import com.tontine.entity.Utilisateur;
import com.tontine.exception.BadRequestException;
import com.tontine.exception.ResourceNotFoundException;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.service.AuditService;
import com.tontine.util.ContratAdminVersion;
import com.tontine.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/utilisateurs")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description = "Recherche d'utilisateurs")
public class UtilisateurController {

    private final UtilisateurRepository utilisateurRepository;
    private final SecurityUtil securityUtil;
    private final AuditService auditService;

    @GetMapping("/rechercher")
    @Operation(summary = "Rechercher des utilisateurs par numéro de téléphone (min 4 chiffres)")
    public ResponseEntity<ApiResponse<List<UtilisateurResponse>>> rechercherParTelephone(
            @RequestParam String telephone) {

        if (telephone == null || telephone.trim().length() < 4) {
            return ResponseEntity.ok(ApiResponse.success(List.of(), "Saisir au moins 4 chiffres"));
        }

        Long currentUserId = securityUtil.getCurrentUserId();
        List<UtilisateurResponse> resultats = utilisateurRepository
                .findTop5ByTelephoneContainingAndActifTrue(telephone.trim())
                .stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .map(u -> UtilisateurResponse.builder()
                        .id(u.getId())
                        .nom(u.getNom())
                        .prenom(u.getPrenom())
                        .telephone(u.getTelephone())
                        .avatarId(u.getAvatarId())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(resultats, "Résultats"));
    }

    @PutMapping("/profil")
    @Operation(summary = "Modifier son nom, prénom ou email")
    public ResponseEntity<ApiResponse<UtilisateurResponse>> updateProfil(
            @Valid @RequestBody UpdateProfilRequest request) {
        Long userId = securityUtil.getCurrentUserId();
        Utilisateur u = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // Vérifier unicité de l'email si modifié
        String nouvelEmail = (request.getEmail() != null && !request.getEmail().isBlank())
                ? request.getEmail().trim() : null;
        if (nouvelEmail != null && !nouvelEmail.equals(u.getEmail())) {
            utilisateurRepository.findByEmail(nouvelEmail).ifPresent(existing -> {
                throw new BadRequestException("Cet email est déjà utilisé par un autre compte");
            });
        }

        u.setNom(request.getNom().trim());
        u.setPrenom(request.getPrenom().trim());
        if (nouvelEmail != null) u.setEmail(nouvelEmail);
        utilisateurRepository.save(u);

        UtilisateurResponse response = UtilisateurResponse.builder()
                .id(u.getId()).nom(u.getNom()).prenom(u.getPrenom())
                .telephone(u.getTelephone()).email(u.getEmail())
                .avatarId(u.getAvatarId())
                .telephoneVerifie(u.getTelephoneVerifie())
                .pinDefini(u.getPinDefini())
                .contratAdminAccepte(ContratAdminVersion.estAcceptee(u.getContratAdminVersion()))
                .createdAt(u.getCreatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Profil mis à jour"));
    }

    @GetMapping("/profil")
    @Operation(summary = "Voir son propre profil")
    public ResponseEntity<ApiResponse<UtilisateurResponse>> getProfil() {
        Long userId = securityUtil.getCurrentUserId();
        Utilisateur u = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        UtilisateurResponse response = UtilisateurResponse.builder()
                .id(u.getId()).nom(u.getNom()).prenom(u.getPrenom())
                .telephone(u.getTelephone()).email(u.getEmail())
                .avatarId(u.getAvatarId())
                .telephoneVerifie(u.getTelephoneVerifie())
                .pinDefini(u.getPinDefini())
                .contratAdminAccepte(ContratAdminVersion.estAcceptee(u.getContratAdminVersion()))
                .createdAt(u.getCreatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Profil"));
    }

    @PostMapping("/contrat-admin/accepter")
    @Operation(summary = "Accepter le contrat admin (commission, amende, garde des fonds) — requis avant de créer une tontine")
    public ResponseEntity<ApiResponse<UtilisateurResponse>> accepterContratAdmin() {
        Long userId = securityUtil.getCurrentUserId();
        Utilisateur u = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        u.setContratAdminVersion(ContratAdminVersion.ACTUELLE);
        u.setContratAdminAccepteLe(LocalDateTime.now());
        utilisateurRepository.save(u);

        auditService.log(u.getId(), u.getTelephone(), "CONTRAT_ADMIN_ACCEPTE", true,
                "version=" + ContratAdminVersion.ACTUELLE);

        UtilisateurResponse response = UtilisateurResponse.builder()
                .id(u.getId()).nom(u.getNom()).prenom(u.getPrenom())
                .telephone(u.getTelephone()).email(u.getEmail())
                .avatarId(u.getAvatarId())
                .telephoneVerifie(u.getTelephoneVerifie())
                .pinDefini(u.getPinDefini())
                .contratAdminAccepte(true)
                .createdAt(u.getCreatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Contrat accepté"));
    }

    @PutMapping("/fcm-token")
    @Operation(summary = "Enregistrer ou rafraîchir le token FCM pour les notifications push")
    public ResponseEntity<ApiResponse<String>> updateFcmToken(
            @Valid @RequestBody FcmTokenRequest request) {
        Long userId = securityUtil.getCurrentUserId();
        Utilisateur u = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
        u.setFcmToken(request.getFcmToken());
        utilisateurRepository.save(u);
        return ResponseEntity.ok(ApiResponse.success(null, "Token FCM enregistré"));
    }

    @DeleteMapping("/fcm-token")
    @Operation(summary = "Supprimer le token FCM (désactiver les notifications push)")
    public ResponseEntity<ApiResponse<String>> deleteFcmToken() {
        Long userId = securityUtil.getCurrentUserId();
        utilisateurRepository.findById(userId).ifPresent(u -> {
            u.setFcmToken(null);
            utilisateurRepository.save(u);
        });
        return ResponseEntity.ok(ApiResponse.success(null, "Notifications push désactivées"));
    }

    @PutMapping("/avatar")
    @Operation(summary = "Mettre à jour l'avatar de l'utilisateur connecté")
    public ResponseEntity<ApiResponse<String>> updateAvatar(@RequestBody Map<String, String> body) {
        Long userId = securityUtil.getCurrentUserId();
        String avatarId = body.get("avatarId");

        Utilisateur u = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
        u.setAvatarId(avatarId);
        utilisateurRepository.save(u);

        return ResponseEntity.ok(ApiResponse.success(avatarId, "Avatar mis à jour"));
    }
}
