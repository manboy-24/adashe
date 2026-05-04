package com.tontine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tontine.dto.request.InscriptionRequest;
import com.tontine.dto.request.OtpRequest;
import com.tontine.dto.response.ApiResponse;
import com.tontine.dto.response.AuthResponse;
import com.tontine.dto.response.UtilisateurResponse;
import com.tontine.security.JwtService;
import com.tontine.service.AuthService;
import com.tontine.service.PinAuthService;
import com.tontine.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        @Order(1)
        SecurityFilterChain authTestChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/auth/inscrire", "/auth/verifier-otp",
                            "/auth/renvoyer-otp", "/auth/pin/**",
                            "/auth/refresh-token")
                    .permitAll()
                    .anyRequest().authenticated());
            return http.build();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private PinAuthService pinAuthService;
    @MockBean private SecurityUtil securityUtil;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;

    // ── POST /auth/inscrire ───────────────────────────────────────────────────

    @Test
    void inscrire_requete_valide_retourne_201() throws Exception {
        InscriptionRequest req = new InscriptionRequest();
        req.setNom("Kamga");
        req.setPrenom("Paul");
        req.setTelephone("+237699000001");

        when(authService.inscrire(any())).thenReturn(
                ApiResponse.success(null, "Code envoyé"));

        mockMvc.perform(post("/auth/inscrire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void inscrire_telephone_invalide_retourne_400() throws Exception {
        InscriptionRequest req = new InscriptionRequest();
        req.setNom("Kamga");
        req.setPrenom("Paul");
        req.setTelephone("abc"); // format invalide

        mockMvc.perform(post("/auth/inscrire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inscrire_nom_vide_retourne_400() throws Exception {
        InscriptionRequest req = new InscriptionRequest();
        req.setNom(""); // @NotBlank
        req.setPrenom("Paul");
        req.setTelephone("+237699000001");

        mockMvc.perform(post("/auth/inscrire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inscrire_corps_vide_retourne_400() throws Exception {
        mockMvc.perform(post("/auth/inscrire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /auth/verifier-otp ───────────────────────────────────────────────

    @Test
    void verifierOtp_requete_valide_retourne_200() throws Exception {
        OtpRequest req = new OtpRequest();
        req.setTelephone("+237699000001");
        req.setCode("123456");

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("jwt-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .pinDefini(false)
                .utilisateur(UtilisateurResponse.builder()
                        .id(1L).nom("Kamga").prenom("Paul")
                        .telephone("+237699000001")
                        .telephoneVerifie(true)
                        .createdAt(LocalDateTime.now())
                        .build())
                .build();

        when(authService.verifierOtp(any())).thenReturn(
                ApiResponse.success(authResponse, "Téléphone vérifié"));

        mockMvc.perform(post("/auth/verifier-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"));
    }

    // ── POST /auth/logout ─────────────────────────────────────────────────────

    @Test
    void logout_sans_jwt_retourne_403() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "+237699000001")
    void logout_avec_jwt_retourne_200() throws Exception {
        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(authService.deconnecter(1L)).thenReturn(
                ApiResponse.success(null, "Déconnecté"));

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ── POST /auth/renvoyer-otp ───────────────────────────────────────────────

    @Test
    void renvoyerOtp_sans_parametre_retourne_400() throws Exception {
        mockMvc.perform(post("/auth/renvoyer-otp"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renvoyerOtp_avec_telephone_retourne_200() throws Exception {
        when(authService.renvoyerOtp(any())).thenReturn(
                ApiResponse.success(null, "Nouveau code envoyé"));

        mockMvc.perform(post("/auth/renvoyer-otp")
                        .param("telephone", "+237699000001"))
                .andExpect(status().isOk());
    }
}
