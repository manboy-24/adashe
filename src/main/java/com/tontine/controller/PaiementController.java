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

    // ── Pages de retour (redirect depuis le widget Monetbil) ─────────────────

    @GetMapping(value = "/succes", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(hidden = true)
    public ResponseEntity<String> paiementSucces() {
        return ResponseEntity.ok(pagePaiement("✅ Paiement confirmé",
                "Votre paiement a été confirmé avec succès. Vous pouvez retourner sur l'application.",
                "#27ae60"));
    }

    @GetMapping(value = "/echec", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(hidden = true)
    public ResponseEntity<String> paiementEchec() {
        return ResponseEntity.ok(pagePaiement("❌ Paiement annulé",
                "Le paiement a été annulé ou a échoué. Vous pouvez réessayer depuis l'application.",
                "#e74c3c"));
    }

    private String pagePaiement(String titre, String message, String couleur) {
        return "<!DOCTYPE html><html lang='fr'><head><meta charset='UTF-8'/>" +
               "<meta name='viewport' content='width=device-width,initial-scale=1'/>" +
               "<title>" + titre + "</title>" +
               "<style>body{font-family:sans-serif;display:flex;align-items:center;" +
               "justify-content:center;min-height:100vh;margin:0;background:#f5f5f5;}" +
               ".card{background:#fff;border-radius:12px;padding:40px 32px;text-align:center;" +
               "max-width:380px;box-shadow:0 4px 20px rgba(0,0,0,.08);}" +
               "h1{color:" + couleur + ";font-size:1.5rem;margin-bottom:12px;}" +
               "p{color:#555;line-height:1.6;}" +
               "a.btn{display:inline-block;margin-top:24px;padding:12px 28px;" +
               "background:" + couleur + ";color:#fff;border-radius:8px;text-decoration:none;" +
               "font-weight:600;}</style></head>" +
               "<body><div class='card'><h1>" + titre + "</h1><p>" + message + "</p>" +
               "<a class='btn' href='intent://home#Intent;scheme=adashecash;" +
               "package=com.tontineplus.app;end'>Retour à l'app</a></div>" +
               "<script>setTimeout(function(){" +
               "window.location='intent://home#Intent;scheme=adashecash;package=com.tontineplus.app;end'" +
               "},800);</script></body></html>";
    }
}
