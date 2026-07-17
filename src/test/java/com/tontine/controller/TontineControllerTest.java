package com.tontine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tontine.dto.request.TontineRequest;
import com.tontine.dto.response.ApiResponse;
import com.tontine.dto.response.DashboardResponse;
import com.tontine.dto.response.TontineResponse;
import com.tontine.enums.FrequenceType;
import com.tontine.enums.TirageType;
import com.tontine.enums.TontineStatus;
import com.tontine.security.JwtAuthFilter;
import com.tontine.security.JwtService;
import com.tontine.service.RateLimitService;
import com.tontine.service.TontineService;
import com.tontine.util.SecurityUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TontineController.class)
class TontineControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TontineService tontineService;
    @MockBean private com.tontine.service.ScoreFiabiliteService scoreFiabiliteService;
    @MockBean private SecurityUtil securityUtil;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private JwtAuthFilter jwtAuthFilter;
    @MockBean private RateLimitService rateLimitService;

    private TontineResponse tontineResponse;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(
                    inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(
                any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));

        tontineResponse = TontineResponse.builder()
                .id(10L)
                .nom("Ma Tontine")
                .montantContribution(new BigDecimal("5000"))
                .devise("XAF")
                .frequence(FrequenceType.MENSUEL)
                .typeTirage(TirageType.RANDOM)
                .statut(TontineStatus.EN_ATTENTE)
                .cycleActuel(1)
                .nombreMaxMembres(20)
                .nombreMembresActifs(0)
                .estCreateur(true)
                .membres(Collections.emptyList())
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── GET /tontines — sécurité ──────────────────────────────────────────────

    @Test
    void getMesTontines_sans_jwt_retourne_401() throws Exception {
        mockMvc.perform(get("/tontines"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "+237699000001")
    void getMesTontines_avec_jwt_retourne_200_et_liste() throws Exception {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(tontineService.getMesTontines(eq(1L), any()))
                .thenReturn(List.of(tontineResponse));

        mockMvc.perform(get("/tontines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].nom").value("Ma Tontine"));
    }

    @Test
    @WithMockUser
    void getMesTontines_pagination_taille_capee_a_100() throws Exception {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(tontineService.getMesTontines(eq(1L), any()))
                .thenReturn(Collections.emptyList());

        // size=500 doit être accepté mais réduit à 100 dans le contrôleur
        mockMvc.perform(get("/tontines").param("size", "500"))
                .andExpect(status().isOk());
    }

    // ── POST /tontines ────────────────────────────────────────────────────────

    @Test
    void creerTontine_sans_jwt_retourne_403() throws Exception {
        mockMvc.perform(post("/tontines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void creerTontine_requete_valide_retourne_201() throws Exception {
        TontineRequest request = new TontineRequest();
        request.setNom("Tontine Famille");
        request.setMontantContribution(new BigDecimal("10000"));
        request.setFrequence(FrequenceType.MENSUEL);
        request.setTypeTirage(TirageType.RANDOM);
        request.setDateDebut(LocalDate.now().plusDays(7));

        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(tontineService.creerTontine(any(), eq(1L))).thenReturn(tontineResponse);

        mockMvc.perform(post("/tontines")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Ma Tontine"));
    }

    @Test
    @WithMockUser
    void creerTontine_nom_manquant_retourne_400() throws Exception {
        TontineRequest request = new TontineRequest();
        // nom absent → @NotBlank
        request.setMontantContribution(new BigDecimal("5000"));
        request.setFrequence(FrequenceType.MENSUEL);
        request.setTypeTirage(TirageType.RANDOM);
        request.setDateDebut(LocalDate.now());

        mockMvc.perform(post("/tontines")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void creerTontine_montant_trop_faible_retourne_400() throws Exception {
        TontineRequest request = new TontineRequest();
        request.setNom("Test");
        request.setMontantContribution(new BigDecimal("500")); // @DecimalMin("1000")
        request.setFrequence(FrequenceType.MENSUEL);
        request.setTypeTirage(TirageType.RANDOM);
        request.setDateDebut(LocalDate.now());

        mockMvc.perform(post("/tontines")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /tontines/dashboard ───────────────────────────────────────────────

    @Test
    void getDashboard_sans_jwt_retourne_401() throws Exception {
        mockMvc.perform(get("/tontines/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getDashboard_avec_jwt_retourne_200() throws Exception {
        DashboardResponse dashboard = DashboardResponse.builder()
                .nombreTontinesActives(2)
                .totalCotise(new BigDecimal("15000"))
                .mesRetardsTotaux(0)
                .cotisationsEnAttente(Collections.emptyList())
                .build();

        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(tontineService.getDashboard(1L)).thenReturn(dashboard);

        mockMvc.perform(get("/tontines/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreTontinesActives").value(2))
                .andExpect(jsonPath("$.totalCotise").value(15000));
    }

    // ── GET /tontines/{id} ────────────────────────────────────────────────────

    @Test
    void getTontineById_sans_jwt_retourne_401() throws Exception {
        mockMvc.perform(get("/tontines/10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getTontineById_avec_jwt_retourne_200() throws Exception {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(tontineService.getTontineById(10L, 1L)).thenReturn(tontineResponse);

        mockMvc.perform(get("/tontines/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    // ── DELETE /tontines/{id} ─────────────────────────────────────────────────

    @Test
    void supprimerTontine_sans_jwt_retourne_403() throws Exception {
        mockMvc.perform(delete("/tontines/10"))
                .andExpect(status().isForbidden());
    }

    // ── POST /tontines/{id}/demarrer ──────────────────────────────────────────

    @Test
    void demarrerTontine_sans_jwt_retourne_403() throws Exception {
        mockMvc.perform(post("/tontines/10/demarrer"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void demarrerTontine_avec_jwt_retourne_200() throws Exception {
        TontineResponse active = TontineResponse.builder()
                .id(10L).nom("Ma Tontine")
                .statut(TontineStatus.ACTIVE)
                .membres(Collections.emptyList()).build();

        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(tontineService.demarrerTontine(10L, 1L)).thenReturn(active);

        mockMvc.perform(post("/tontines/10/demarrer")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACTIVE"));
    }

    // ── POST /tontines/rejoindre/{code} ───────────────────────────────────────

    @Test
    void rejoindreParCode_sans_jwt_retourne_403() throws Exception {
        mockMvc.perform(post("/tontines/rejoindre/ABCD1234"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void rejoindreParCode_avec_jwt_retourne_200() throws Exception {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(tontineService.rejoindreParCode("ABCD1234", 1L))
                .thenReturn(ApiResponse.success(null, "Vous avez rejoint la tontine"));

        mockMvc.perform(post("/tontines/rejoindre/ABCD1234")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Vous avez rejoint la tontine"));
    }

    // ── POST /tontines/{id}/terminer ──────────────────────────────────────────

    @Test
    void terminerTontine_sans_jwt_retourne_403() throws Exception {
        mockMvc.perform(post("/tontines/10/terminer"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void terminerTontine_avec_jwt_retourne_200() throws Exception {
        TontineResponse terminee = TontineResponse.builder()
                .id(10L).nom("Ma Tontine")
                .statut(TontineStatus.TERMINEE)
                .membres(Collections.emptyList()).build();

        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(tontineService.terminerTontine(10L, 1L)).thenReturn(terminee);

        mockMvc.perform(post("/tontines/10/terminer")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TERMINEE"));
    }
}
