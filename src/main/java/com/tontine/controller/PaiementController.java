package com.tontine.controller;

import com.tontine.dto.request.ConfirmerPaiementMonetbilRequest;
import com.tontine.dto.request.PaiementEspecesRequest;
import com.tontine.dto.request.PaiementMobileMoneyRequest;
import com.tontine.dto.response.*;
import com.tontine.service.PaiementService;
import com.tontine.service.impl.PaiementServiceImpl;
import com.tontine.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/paiements")
@RequiredArgsConstructor
@Tag(name = "Paiements", description = "Mobile Money (MTN, Orange via Monetbil) + webhooks")
public class PaiementController {

    private final PaiementService paiementService;
    private final PaiementServiceImpl paiementServiceImpl;
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

    @GetMapping("/simuler-frais")
    @Operation(summary = "Simuler les frais Monetbil pour un montant donné")
    public ResponseEntity<Map<String, Object>> simulerFrais(
            @RequestParam BigDecimal montant) {
        BigDecimal frais  = paiementServiceImpl.calculerFrais(montant);
        BigDecimal total  = paiementServiceImpl.calculerMontantFacture(montant);
        return ResponseEntity.ok(Map.of(
                "montantNet",  montant,
                "fraisGateway", frais,
                "montantTotal", total
        ));
    }

    @PostMapping("/monetbil/confirmer")
    @Operation(summary = "Confirmer un paiement après onPaymentSuccess du SDK Android Monetbil")
    public ResponseEntity<PaiementResponse> confirmerMonetbil(
            @Valid @RequestBody ConfirmerPaiementMonetbilRequest request) {
        return ResponseEntity.ok(
                paiementService.confirmerPaiementMonetbil(request, securityUtil.getCurrentUserId()));
    }

    // ── Webhooks (sans JWT — appelés par Monetbil) ────────────────────────────

    @RequestMapping(value = "/webhook/monetbil", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "Webhook Monetbil — callback GET ou POST de confirmation de paiement")
    public ResponseEntity<ApiResponse<String>> webhookMonetbil(
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paiementService.traiterCallbackMonetbil(params));
    }

    @PostMapping("/webhook/mtn")
    @Operation(summary = "Webhook MTN MADAPI (callback paiement direct)")
    public ResponseEntity<ApiResponse<String>> webhookMtn(
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(paiementService.traiterCallbackMtn(payload));
    }
}
