package com.tontine.controller;

import com.tontine.dto.response.ApiResponse;
import com.tontine.dto.response.AuditLogResponse;
import com.tontine.dto.response.VirementAmendeResponse;
import com.tontine.entity.AuditLog;
import com.tontine.entity.VirementAmende;
import com.tontine.enums.VirementAmendeStatut;
import com.tontine.repository.AuditLogRepository;
import com.tontine.service.impl.VirementAmendeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Endpoints réservés aux administrateurs système")
public class AdminController {

    private final AuditLogRepository auditLogRepository;
    private final VirementAmendeService virementAmendeService;

    /**
     * Liste paginée des logs d'audit avec filtres optionnels.
     *
     * GET /admin/audit-logs?page=0&size=50&action=CONNEXION&statut=FAILURE&userId=42
     *                       &debut=2025-01-01T00:00:00&fin=2025-12-31T23:59:59
     */
    @GetMapping("/audit-logs")
    @Operation(summary = "Consulter les logs d'audit", description = "Filtres : action, statut (SUCCESS/FAILURE), userId, plage de dates.")
    public ApiResponse<List<AuditLogResponse>> getAuditLogs(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "50")  int size,
            @RequestParam(required = false)     String action,
            @RequestParam(required = false)     String statut,
            @RequestParam(required = false)     Long userId,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin) {

        LocalDateTime from = debut != null ? debut : LocalDateTime.now().minusDays(30);
        LocalDateTime to   = fin   != null ? fin   : LocalDateTime.now();

        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(200, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AuditLog> resultPage = auditLogRepository.rechercher(action, statut, userId, from, to, pageable);

        List<AuditLogResponse> data = resultPage.getContent()
                .stream().map(this::toResponse).toList();

        return ApiResponse.success(data,
                "Page " + (page + 1) + "/" + resultPage.getTotalPages()
                + " — " + resultPage.getTotalElements() + " entrée(s)");
    }

    /** Logs d'un utilisateur précis (toutes actions confondues). */
    @GetMapping("/audit-logs/utilisateur/{userId}")
    @Operation(summary = "Logs d'audit par utilisateur")
    public ApiResponse<List<AuditLogResponse>> getAuditLogsParUtilisateur(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(200, size),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        List<AuditLogResponse> data = auditLogRepository
                .findByUserId(userId, pageable)
                .getContent().stream().map(this::toResponse).toList();

        return ApiResponse.success(data, "Logs de l'utilisateur " + userId);
    }

    // ── Virements d'amendes ──────────────────────────────────────────────────

    /**
     * Liste des virements d'amende vers le compte développeur.
     * GET /admin/amendes?statut=ECHEC&page=0&size=20
     */
    @GetMapping("/amendes")
    @Operation(summary = "Lister les virements d'amende", description = "Filtrer par statut : EN_ATTENTE, SUCCES, ECHEC")
    public ApiResponse<List<VirementAmendeResponse>> getVirements(
            @RequestParam(required = false) VirementAmendeStatut statut,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(200, size));
        Page<VirementAmende> resultPage = virementAmendeService.listerVirements(statut, pageable);
        List<VirementAmendeResponse> data = resultPage.getContent()
                .stream().map(this::toVirementResponse).toList();
        return ApiResponse.success(data,
                resultPage.getTotalElements() + " virement(s) — page " + (page + 1) + "/" + resultPage.getTotalPages());
    }

    /**
     * Relancer un virement échoué.
     * POST /admin/amendes/{id}/retenter
     */
    @PostMapping("/amendes/{id}/retenter")
    @Operation(summary = "Relancer un virement d'amende échoué")
    public ApiResponse<VirementAmendeResponse> retenterVirement(@PathVariable Long id) {
        VirementAmende virement = virementAmendeService.retenterVirement(id);
        return ApiResponse.success(toVirementResponse(virement), "Virement relancé");
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private AuditLogResponse toResponse(AuditLog a) {
        return AuditLogResponse.builder()
                .id(a.getId())
                .userId(a.getUserId())
                .telephone(a.getTelephone())
                .action(a.getAction())
                .statut(a.getStatut())
                .ipAddress(a.getIpAddress())
                .details(a.getDetails())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private VirementAmendeResponse toVirementResponse(VirementAmende v) {
        return VirementAmendeResponse.builder()
                .id(v.getId())
                .paiementId(v.getPaiement() != null ? v.getPaiement().getId() : null)
                .montant(v.getMontant())
                .operateur(v.getOperateur())
                .numeroBeneficiaire(v.getNumeroBeneficiaire())
                .referenceTontine(v.getReferenceTontine())
                .statut(v.getStatut())
                .referenceTransfert(v.getReferenceTransfert())
                .messageErreur(v.getMessageErreur())
                .createdAt(v.getCreatedAt())
                .dateVirement(v.getDateVirement())
                .build();
    }
}
