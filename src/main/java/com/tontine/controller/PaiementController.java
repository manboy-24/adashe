package com.tontine.controller;

import com.tontine.dto.request.PaiementEspecesRequest;
import com.tontine.dto.request.PaiementMobileMoneyRequest;
import com.tontine.dto.response.*;
import com.tontine.service.PaiementService;
import com.tontine.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/paiements")
@RequiredArgsConstructor
@Tag(name = "Paiements", description = "Mobile Money (MTN, Orange via Monetbil) + webhooks")
public class PaiementController {

    private final PaiementService paiementService;
    private final SecurityUtil securityUtil;

    @PostMapping("/initier")
    @Operation(summary = "Initier un paiement Mobile Money (MTN ou Orange)")
    public ResponseEntity<PaiementResponse> initier(
            @Valid @RequestBody PaiementMobileMoneyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paiementService.initierPaiement(request, securityUtil.getCurrentUserId()));
    }

    @PostMapping("/especes/initier")
    @Operation(summary = "Admin paie en MoMo pour un membre qui a remis du cash — affiché 'Espèces' des deux côtés")
    public ResponseEntity<PaiementResponse> initierPaiementEspeces(
            @Valid @RequestBody PaiementEspecesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paiementService.initierPaiementEspeces(request, securityUtil.getCurrentUserId()));
    }

    @GetMapping("/mes-paiements")
    @Operation(summary = "Mes paiements")
    public ResponseEntity<List<PaiementResponse>> mesPaiements() {
        return ResponseEntity.ok(paiementService.getMesPaiements(securityUtil.getCurrentUserId()));
    }

    // ── Webhooks (sans JWT — appelés par Monetbil) ────────────────────────────

    @PostMapping("/webhook/monetbil")
    @Operation(summary = "Webhook Monetbil (MTN MoMo + Orange Money Cameroun)")
    public ResponseEntity<ApiResponse<String>> webhookMonetbil(
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paiementService.traiterCallbackMonetbil(params));
    }
}
