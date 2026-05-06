package com.tontine.controller;

import com.tontine.dto.request.CompteWalletRequest;
import com.tontine.dto.response.CompteWalletResponse;
import com.tontine.service.WalletService;
import com.tontine.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Gestion des comptes Mobile Money de l'utilisateur")
public class WalletController {

    private final WalletService walletService;
    private final SecurityUtil  securityUtil;

    @GetMapping("/mes-comptes")
    @Operation(summary = "Lister mes comptes wallet (Orange Money, MTN MoMo, Espèces)")
    public ResponseEntity<List<CompteWalletResponse>> mesComptes() {
        return ResponseEntity.ok(walletService.getMesComptes(securityUtil.getCurrentUserId()));
    }

    @PostMapping("/comptes")
    @Operation(summary = "Créer ou mettre à jour un compte wallet")
    public ResponseEntity<CompteWalletResponse> sauvegarderCompte(
            @Valid @RequestBody CompteWalletRequest request) {
        return ResponseEntity.ok(
                walletService.sauvegarderCompte(securityUtil.getCurrentUserId(), request)
        );
    }
}
