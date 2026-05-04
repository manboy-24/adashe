package com.tontine.controller;

import com.tontine.dto.request.*;
import com.tontine.dto.response.*;
import com.tontine.service.PinAuthService;
import com.tontine.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints PIN uniquement.
 * NB : /auth/pin/** dans AuthController couvre déjà connexion/reset.
 * Ce controller ajoute : changer son PIN (utilisateur connecté).
 */
@RestController
@RequestMapping("/pin")
@RequiredArgsConstructor
@Tag(name = "PIN", description = "Gestion du PIN (changement, vérification)")
public class PinAuthController {

    private final PinAuthService pinAuthService;
    private final SecurityUtil securityUtil;  // bean injecté, pas statique

    @PutMapping("/changer")
    @Operation(summary = "Changer son PIN (utilisateur connecté)")
    public ResponseEntity<ApiResponse<String>> changerPin(
            @Valid @RequestBody ChangerPinRequest request) {
        return ResponseEntity.ok(pinAuthService.changerPin(request, securityUtil.getCurrentUserId()));
    }
}
