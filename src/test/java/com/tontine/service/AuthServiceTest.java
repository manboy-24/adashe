package com.tontine.service;

import com.tontine.dto.request.InscriptionRequest;
import com.tontine.dto.request.OtpRequest;
import com.tontine.dto.response.ApiResponse;
import com.tontine.dto.response.AuthResponse;
import com.tontine.entity.Utilisateur;
import com.tontine.exception.BadRequestException;
import com.tontine.exception.ResourceNotFoundException;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.security.JwtService;
import com.tontine.service.EmailAsyncService;
import com.tontine.service.SmsAsyncService;
import com.tontine.service.impl.AuthServiceImpl;
import com.tontine.util.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private UserDetailsService userDetailsService;
    @Mock private NotificationService notificationService;
    @Mock private SmsAsyncService smsAsyncService;
    @Mock private EmailAsyncService emailAsyncService;
    @Mock private com.tontine.repository.SessionRepository sessionRepository;
    @Mock private com.tontine.service.AuditService auditService;
    @Mock private SecurityUtil securityUtil;

    @InjectMocks
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "otpExpiration",  5);
        ReflectionTestUtils.setField(service, "securityUtil",   securityUtil);
    }

    // ── inscrire ──────────────────────────────────────────────────────────────

    @Test
    void inscrire_succes_envoie_sms() {
        when(utilisateurRepository.existsByTelephone("699000001")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedOtp");
        when(utilisateurRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InscriptionRequest req = new InscriptionRequest();
        req.setNom("Kamga");
        req.setPrenom("Paul");
        req.setTelephone("699000001");

        ApiResponse<String> result = service.inscrire(req);

        assertTrue(result.isSuccess());
        verify(smsAsyncService).envoyerSmsAsync(eq("699000001"), anyString());
        verify(utilisateurRepository).save(argThat(u ->
            u.getTelephone().equals("699000001") &&
            u.getOtpCode().equals("hashedOtp") &&
            "INSCRIPTION".equals(u.getOtpPurpose())
        ));
    }

    @Test
    void inscrire_telephone_deja_utilise_leve_exception() {
        when(utilisateurRepository.existsByTelephone("699000001")).thenReturn(true);

        InscriptionRequest req = new InscriptionRequest();
        req.setNom("Kamga");
        req.setPrenom("Paul");
        req.setTelephone("699000001");

        assertThrows(BadRequestException.class, () -> service.inscrire(req));
        verify(utilisateurRepository, never()).save(any());
        verify(smsAsyncService, never()).envoyerSmsAsync(any(), any());
    }

    @Test
    void inscrire_email_deja_utilise_leve_exception() {
        when(utilisateurRepository.existsByTelephone("699000001")).thenReturn(false);
        when(utilisateurRepository.existsByEmail("test@gmail.com")).thenReturn(true);

        InscriptionRequest req = new InscriptionRequest();
        req.setNom("Kamga");
        req.setPrenom("Paul");
        req.setTelephone("699000001");
        req.setEmail("test@gmail.com");

        assertThrows(BadRequestException.class, () -> service.inscrire(req));
        verify(utilisateurRepository, never()).save(any());
    }

    // ── verifierOtp ───────────────────────────────────────────────────────────

    @Test
    void verifierOtp_succes_marque_telephone_verifie() {
        Utilisateur u = Utilisateur.builder()
                .id(1L).telephone("699000001")
                .otpCode("hashedOtp")
                .otpExpiration(LocalDateTime.now().plusMinutes(5))
                .otpPurpose("INSCRIPTION")
                .pinDefini(false)
                .build();

        when(utilisateurRepository.findByTelephone("699000001")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("123456", "hashedOtp")).thenReturn(true);
        when(utilisateurRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);
        when(userDetailsService.loadUserByUsername("699000001"))
                .thenReturn(org.springframework.security.core.userdetails.User
                        .withUsername("699000001").password("x").roles("USER").build());

        OtpRequest req = new OtpRequest();
        req.setTelephone("699000001");
        req.setCode("123456");

        ApiResponse<AuthResponse> result = service.verifierOtp(req);

        assertTrue(result.isSuccess());
        assertTrue(u.getTelephoneVerifie());
        assertNull(u.getOtpCode());
    }

    @Test
    void verifierOtp_code_invalide_leve_exception() {
        Utilisateur u = Utilisateur.builder()
                .id(1L).telephone("699000001")
                .otpCode("hashedOtp")
                .otpExpiration(LocalDateTime.now().plusMinutes(5))
                .build();

        when(utilisateurRepository.findByTelephone("699000001")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("000000", "hashedOtp")).thenReturn(false);

        OtpRequest req = new OtpRequest();
        req.setTelephone("699000001");
        req.setCode("000000");

        assertThrows(BadRequestException.class, () -> service.verifierOtp(req));
        assertFalse(Boolean.TRUE.equals(u.getTelephoneVerifie()));
    }

    @Test
    void verifierOtp_expire_leve_exception() {
        Utilisateur u = Utilisateur.builder()
                .id(1L).telephone("699000001")
                .otpCode("hashedOtp")
                .otpExpiration(LocalDateTime.now().minusMinutes(1)) // expiré
                .build();

        when(utilisateurRepository.findByTelephone("699000001")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("123456", "hashedOtp")).thenReturn(true);

        OtpRequest req = new OtpRequest();
        req.setTelephone("699000001");
        req.setCode("123456");

        assertThrows(BadRequestException.class, () -> service.verifierOtp(req));
    }

    @Test
    void verifierOtp_utilisateur_inexistant_leve_exception() {
        when(utilisateurRepository.findByTelephone(anyString())).thenReturn(Optional.empty());

        OtpRequest req = new OtpRequest();
        req.setTelephone("699999999");
        req.setCode("123456");

        assertThrows(ResourceNotFoundException.class, () -> service.verifierOtp(req));
    }

    // ── renvoyerOtp ───────────────────────────────────────────────────────────

    @Test
    void renvoyerOtp_succes_genere_nouveau_code() {
        Utilisateur u = Utilisateur.builder()
                .id(1L).telephone("699000001")
                .otpExpiration(null) // aucun code actif
                .build();

        when(utilisateurRepository.findByTelephone("699000001")).thenReturn(Optional.of(u));
        when(passwordEncoder.encode(anyString())).thenReturn("newHashedOtp");
        when(utilisateurRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ApiResponse<String> result = service.renvoyerOtp("699000001");

        assertTrue(result.isSuccess());
        verify(smsAsyncService).envoyerSmsAsync(eq("699000001"), anyString());
        assertEquals("newHashedOtp", u.getOtpCode());
    }

    @Test
    void renvoyerOtp_code_encore_valide_leve_exception() {
        // Code envoyé il y a 30s — trop tôt pour en demander un nouveau
        Utilisateur u = Utilisateur.builder()
                .id(1L).telephone("699000001")
                .otpExpiration(LocalDateTime.now().plusMinutes(4).plusSeconds(31))
                .build();

        when(utilisateurRepository.findByTelephone("699000001")).thenReturn(Optional.of(u));

        assertThrows(BadRequestException.class, () -> service.renvoyerOtp("699000001"));
        verify(smsAsyncService, never()).envoyerSmsAsync(any(), any());
    }

    // ── deconnecter ───────────────────────────────────────────────────────────

    @Test
    void deconnecter_efface_refresh_token() {
        Utilisateur u = Utilisateur.builder()
                .id(1L).telephone("699000001")
                .refreshToken("someHash")
                .refreshTokenExpiration(LocalDateTime.now().plusDays(7))
                .build();

        when(securityUtil.getCurrentUserId()).thenReturn(1L);
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(u));
        when(utilisateurRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ApiResponse<String> result = service.deconnecter(1L);

        assertTrue(result.isSuccess());
        assertNull(u.getRefreshToken());
        assertNull(u.getRefreshTokenExpiration());
    }

    @Test
    void deconnecter_autre_utilisateur_leve_ForbiddenException() {
        when(securityUtil.getCurrentUserId()).thenReturn(2L);

        assertThrows(com.tontine.exception.ForbiddenException.class,
                () -> service.deconnecter(1L));
        verify(utilisateurRepository, never()).findById(any());
    }
}
