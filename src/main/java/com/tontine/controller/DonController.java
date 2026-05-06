package com.tontine.controller;

import com.tontine.dto.request.DonRequest;
import com.tontine.dto.response.ApiResponse;
import com.tontine.dto.response.DonResponse;
import com.tontine.service.DonService;
import com.tontine.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/dons")
@RequiredArgsConstructor
@Tag(name = "Dons", description = "Dons utilisateurs via Mobile Money (MTN MoMo, Orange Money)")
public class DonController {

    private final DonService  donService;
    private final SecurityUtil securityUtil;

    @PostMapping("/initier")
    @Operation(summary = "Initier un don via Mobile Money")
    public ResponseEntity<DonResponse> initierDon(@Valid @RequestBody DonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(donService.initierDon(request, securityUtil.getCurrentUserId()));
    }

    @PostMapping("/webhook/monetbil")
    @Operation(summary = "Webhook Monetbil pour les dons (sans JWT)")
    public ResponseEntity<ApiResponse<String>> webhookDon(
            @RequestParam Map<String, String> params) {
        return ResponseEntity.ok(donService.traiterCallbackDon(params));
    }
}
