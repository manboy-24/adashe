package com.tontine.controller;

import com.tontine.dto.request.*;
import com.tontine.dto.response.*;
import com.tontine.service.TontineService;
import com.tontine.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/tontines")
@RequiredArgsConstructor
@Tag(name = "Tontines", description = "Gestion des tontines, membres, cotisations, tirages")
public class TontineController {

    private final TontineService tontineService;
    private final SecurityUtil securityUtil;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @Operation(summary = "Vue d'ensemble de l'utilisateur courant : tontines actives, total cotisé, prochain tirage, cotisations dues")
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(tontineService.getDashboard(securityUtil.getCurrentUserId()));
    }

    // ── Tontines ──────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Créer une tontine")
    public ResponseEntity<TontineResponse> creer(@Valid @RequestBody TontineRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tontineService.creerTontine(request, securityUtil.getCurrentUserId()));
    }

    @GetMapping
    @Operation(summary = "Mes tontines (celles où je suis membre) — ?page=0&size=50")
    public ResponseEntity<List<TontineResponse>> getMes(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending());
        return ResponseEntity.ok(tontineService.getMesTontines(securityUtil.getCurrentUserId(), pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une tontine")
    public ResponseEntity<TontineResponse> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(tontineService.getTontineById(id, securityUtil.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une tontine — nom/description/prochain tirage toujours modifiables ; montant/fréquence/typeTirage verrouillés après démarrage")
    public ResponseEntity<TontineResponse> modifier(
            @PathVariable Long id,
            @Valid @RequestBody ModifierTontineRequest request) {
        return ResponseEntity.ok(tontineService.modifierTontine(id, request, securityUtil.getCurrentUserId()));
    }

    @PutMapping("/{id}/configurer")
    @Operation(summary = "Configurer la tontine : commission (%), numéros MTN/Orange Mobile Money (créateur uniquement)")
    public ResponseEntity<TontineResponse> configurer(
            @PathVariable Long id,
            @Valid @RequestBody ConfigurerTontineRequest request) {
        return ResponseEntity.ok(tontineService.configurerTontine(id, request, securityUtil.getCurrentUserId()));
    }

    @PostMapping("/{id}/demarrer")
    @Operation(summary = "Démarrer la tontine (EN_ATTENTE → ACTIVE, min 2 membres requis)")
    public ResponseEntity<TontineResponse> demarrer(@PathVariable Long id) {
        return ResponseEntity.ok(tontineService.demarrerTontine(id, securityUtil.getCurrentUserId()));
    }

    @PostMapping("/{id}/terminer")
    @Operation(summary = "Terminer manuellement une tontine ACTIVE (créateur)")
    public ResponseEntity<TontineResponse> terminer(@PathVariable Long id) {
        return ResponseEntity.ok(tontineService.terminerTontine(id, securityUtil.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une tontine (créateur, si aucune cotisation ou tontine terminée)")
    public ResponseEntity<ApiResponse<String>> supprimer(@PathVariable Long id) {
        return ResponseEntity.ok(tontineService.supprimerTontine(id, securityUtil.getCurrentUserId()));
    }

    // ── Membres ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/membres")
    @Operation(summary = "Ajouter un membre (créateur/admin)")
    public ResponseEntity<MembreResponse> ajouterMembre(
            @PathVariable Long id,
            @Valid @RequestBody AjoutMembreRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tontineService.ajouterMembre(id, request, securityUtil.getCurrentUserId()));
    }

    @PostMapping("/rejoindre/{code}")
    @Operation(summary = "Rejoindre une tontine avec le code d'invitation")
    public ResponseEntity<ApiResponse<String>> rejoindre(@PathVariable String code) {
        return ResponseEntity.ok(tontineService.rejoindreParCode(code, securityUtil.getCurrentUserId()));
    }

    @PostMapping("/{tontineId}/membres/accepter")
    @Operation(summary = "Accepter une invitation à rejoindre la tontine (membre invité)")
    public ResponseEntity<ApiResponse<String>> accepterInvitation(@PathVariable Long tontineId) {
        return ResponseEntity.ok(tontineService.accepterInvitation(tontineId, securityUtil.getCurrentUserId()));
    }

    @PostMapping("/{tontineId}/membres/decliner")
    @Operation(summary = "Décliner une invitation à rejoindre la tontine (membre invité)")
    public ResponseEntity<ApiResponse<String>> declinerInvitation(@PathVariable Long tontineId) {
        return ResponseEntity.ok(tontineService.declinerInvitation(tontineId, securityUtil.getCurrentUserId()));
    }

    @DeleteMapping("/{tontineId}/membres/{membreId}")
    @Operation(summary = "Retirer un membre (créateur/admin)")
    public ResponseEntity<ApiResponse<String>> retirerMembre(
            @PathVariable Long tontineId,
            @PathVariable Long membreId) {
        return ResponseEntity.ok(tontineService.retirerMembre(tontineId, membreId, securityUtil.getCurrentUserId()));
    }

    // ── Cotisations (créateur uniquement) ─────────────────────────────────────

    @PostMapping("/cotisations")
    @Operation(summary = "Enregistrer une cotisation manuelle (créateur)")
    public ResponseEntity<CotisationResponse> enregistrerCotisation(
            @Valid @RequestBody CotisationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tontineService.enregistrerCotisation(request, securityUtil.getCurrentUserId()));
    }

    @GetMapping("/{id}/cotisations/export.csv")
    @Operation(summary = "Exporter toutes les cotisations en CSV (membres de la tontine)")
    public ResponseEntity<byte[]> exportCotisationsCsv(@PathVariable Long id) {
        byte[] csv = tontineService.exportCotisationsCsv(id, securityUtil.getCurrentUserId());
        String filename = "cotisations-tontine-" + id + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/{id}/cotisations")
    @Operation(summary = "Liste des cotisations — ?page=0&size=100")
    public ResponseEntity<List<CotisationResponse>> getCotisations(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "100") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending());
        return ResponseEntity.ok(tontineService.getCotisationsTontine(id, securityUtil.getCurrentUserId(), pageable));
    }

    // ── Tirage (créateur uniquement) ──────────────────────────────────────────

    @PostMapping("/{id}/tirage")
    @Operation(summary = "Effectuer le tirage du cycle (créateur)")
    public ResponseEntity<TirageResponse> tirage(
            @PathVariable Long id,
            @Valid @RequestBody TirageRequest request) {
        return ResponseEntity.ok(tontineService.effectuerTirage(id, request, securityUtil.getCurrentUserId()));
    }

    @PostMapping("/{tontineId}/tirages/{tirageId}/confirmer")
    @Operation(summary = "Valider le paiement au bénéficiaire — avance le cycle et envoie les notifications (créateur/admin)")
    public ResponseEntity<TirageResponse> confirmerTirage(
            @PathVariable Long tontineId,
            @PathVariable Long tirageId) {
        return ResponseEntity.ok(
                tontineService.confirmerTirage(tontineId, tirageId, securityUtil.getCurrentUserId()));
    }

    @GetMapping("/{id}/tirages")
    @Operation(summary = "Historique des tirages")
    public ResponseEntity<List<TirageResponse>> historiqueTirages(@PathVariable Long id) {
        return ResponseEntity.ok(tontineService.getHistoriqueTirages(id, securityUtil.getCurrentUserId()));
    }

    // ── Statistiques (créateur uniquement) ───────────────────────────────────

    @GetMapping("/{id}/statistiques")
    @Operation(summary = "Statistiques de la tontine (créateur)")
    public ResponseEntity<StatistiquesResponse> statistiques(@PathVariable Long id) {
        return ResponseEntity.ok(tontineService.getStatistiques(id, securityUtil.getCurrentUserId()));
    }
}
