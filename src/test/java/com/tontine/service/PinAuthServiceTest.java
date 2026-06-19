package com.tontine.service;

import com.tontine.dto.request.ChangerPinRequest;
import com.tontine.dto.request.ConnexionPinRequest;
import com.tontine.dto.request.CreationPinRequest;
import com.tontine.dto.request.NouveauPinRequest;
import com.tontine.dto.request.ResetPinRequest;
import com.tontine.dto.response.ApiResponse;
import com.tontine.dto.response.AuthResponse;
import com.tontine.dto.response.ConnexionPinResponse;
import com.tontine.entity.Utilisateur;
import com.tontine.exception.BadRequestException;
import com.tontine.exception.ResourceNotFoundException;
import com.tontine.exception.UnauthorizedException;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.security.JwtService;
import com.tontine.service.impl.AuthServiceImpl;
import com.tontine.service.impl.PinAuthServiceImpl;
import com.tontine.service.SmsAsyncService;
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
class PinAuthServiceTest {

    @Mock private UtilisateurRepository repo;
    @Mock private PasswordEncoder encoder;
    @Mock private JwtService jwtService;
    @Mock private UserDetailsService userDetailsService;
    @Mock private NotificationService notifService;
    @Mock private SmsAsyncService smsAsyncService;
    @Mock private AuthServiceImpl authHelper;
    @Mock private com.tontine.service.AuditService auditService;
    @Mock private com.tontine.service.EmailAsyncService emailAsyncService;

    @InjectMocks
    private PinAuthServiceImpl service;

    private Utilisateur utilisateur;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "otpExpiration", 5);

        utilisateur = Utilisateur.builder()
                .id(1L)
                .nom("Dupont")
                .prenom("Jean")
                .telephone("699000001")
                .telephoneVerifie(true)
                .actif(true)
                .pinDefini(false)
                .tentativesPinEchouees(0)
                .build();
    }

    // ── creerPin ──────────────────────────────────────────────────────────────

    @Test
    void creerPin_succes() {
        when(repo.findById(1L)).thenReturn(Optional.of(utilisateur));
        when(encoder.encode("1234")).thenReturn("hashed1234");
        when(authHelper.genererAuthResponse(any())).thenReturn(new AuthResponse());

        CreationPinRequest req = new CreationPinRequest();
        req.setPin("1234");
        req.setConfirmPin("1234");

        ApiResponse<AuthResponse> result = service.creerPin(req, 1L);

        assertTrue(result.isSuccess());
        verify(repo).save(argThat(u -> u.getPinDefini() && u.getCodePin().equals("hashed1234")));
    }

    @Test
    void creerPin_pins_differents_leve_exception() {
        CreationPinRequest req = new CreationPinRequest();
        req.setPin("1234");
        req.setConfirmPin("5678");

        assertThrows(BadRequestException.class, () -> service.creerPin(req, 1L));
        verify(repo, never()).save(any());
    }

    @Test
    void creerPin_telephone_non_verifie_leve_exception() {
        utilisateur.setTelephoneVerifie(false);
        when(repo.findById(1L)).thenReturn(Optional.of(utilisateur));

        CreationPinRequest req = new CreationPinRequest();
        req.setPin("1234");
        req.setConfirmPin("1234");

        assertThrows(BadRequestException.class, () -> service.creerPin(req, 1L));
    }

    // ── connecterAvecPin ──────────────────────────────────────────────────────

    @Test
    void connecterAvecPin_succes() {
        utilisateur.setPinDefini(true);
        utilisateur.setCodePin("hashed1234");

        when(repo.findByTelephone("699000001")).thenReturn(Optional.of(utilisateur));
        when(encoder.matches("1234", "hashed1234")).thenReturn(true);
        when(authHelper.genererAuthResponse(any())).thenReturn(new AuthResponse());

        ConnexionPinRequest req = new ConnexionPinRequest();
        req.setTelephone("699000001");
        req.setPin("1234");

        ApiResponse<ConnexionPinResponse> result = service.connecterAvecPin(req);

        assertTrue(result.isSuccess());
        verify(repo).save(argThat(u -> u.getTentativesPinEchouees() == 0));
    }

    @Test
    void connecterAvecPin_pin_incorrect_incremente_tentatives() {
        utilisateur.setPinDefini(true);
        utilisateur.setCodePin("hashed1234");

        Utilisateur apresIncrement = Utilisateur.builder()
                .id(1L).telephone("699000001").pinDefini(true).codePin("hashed1234")
                .actif(true).tentativesPinEchouees(1).build();

        when(repo.findByTelephone("699000001")).thenReturn(Optional.of(utilisateur));
        when(encoder.matches("9999", "hashed1234")).thenReturn(false);
        doNothing().when(repo).incrementTentativesEchouees(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(apresIncrement));

        ConnexionPinRequest req = new ConnexionPinRequest();
        req.setTelephone("699000001");
        req.setPin("9999");

        assertThrows(UnauthorizedException.class, () -> service.connecterAvecPin(req));
        verify(repo).incrementTentativesEchouees(1L);
    }

    @Test
    void connecterAvecPin_bloque_apres_5_tentatives() {
        utilisateur.setPinDefini(true);
        utilisateur.setCodePin("hashed1234");
        utilisateur.setTentativesPinEchouees(4);

        Utilisateur apresIncrement = Utilisateur.builder()
                .id(1L).telephone("699000001").pinDefini(true).codePin("hashed1234")
                .actif(true).tentativesPinEchouees(5).build();

        when(repo.findByTelephone("699000001")).thenReturn(Optional.of(utilisateur));
        when(encoder.matches("9999", "hashed1234")).thenReturn(false);
        doNothing().when(repo).incrementTentativesEchouees(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(apresIncrement));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        ConnexionPinRequest req = new ConnexionPinRequest();
        req.setTelephone("699000001");
        req.setPin("9999");

        assertThrows(UnauthorizedException.class, () -> service.connecterAvecPin(req));
        verify(repo).save(argThat(u -> u.getPinBloqueJusquA() != null));
    }

    @Test
    void connecterAvecPin_compte_bloque_leve_exception() {
        utilisateur.setPinDefini(true);
        utilisateur.setCodePin("hashed1234");
        utilisateur.setTentativesPinEchouees(5);
        utilisateur.setPinBloqueJusquA(LocalDateTime.now().plusMinutes(10));

        when(repo.findByTelephone("699000001")).thenReturn(Optional.of(utilisateur));

        ConnexionPinRequest req = new ConnexionPinRequest();
        req.setTelephone("699000001");
        req.setPin("1234");

        assertThrows(UnauthorizedException.class, () -> service.connecterAvecPin(req));
        verify(encoder, never()).matches(any(), any());
    }

    @Test
    void connecterAvecPin_utilisateur_inexistant_leve_exception() {
        when(repo.findByTelephone(anyString())).thenReturn(Optional.empty());

        ConnexionPinRequest req = new ConnexionPinRequest();
        req.setTelephone("699999999");
        req.setPin("1234");

        assertThrows(UnauthorizedException.class, () -> service.connecterAvecPin(req));
    }

    // ── changerPin ────────────────────────────────────────────────────────────

    @Test
    void changerPin_succes() {
        utilisateur.setCodePin("hashedAncien");

        when(repo.findById(1L)).thenReturn(Optional.of(utilisateur));
        when(encoder.matches("1234", "hashedAncien")).thenReturn(true);
        when(encoder.encode("5678")).thenReturn("hashedNouveau");

        ChangerPinRequest req = new ChangerPinRequest();
        req.setAncienPin("1234");
        req.setNouveauPin("5678");

        ApiResponse<String> result = service.changerPin(req, 1L);

        assertTrue(result.isSuccess());
        verify(repo).save(argThat(u -> u.getCodePin().equals("hashedNouveau")));
    }

    @Test
    void changerPin_ancien_pin_incorrect_leve_exception() {
        utilisateur.setCodePin("hashedAncien");

        when(repo.findById(1L)).thenReturn(Optional.of(utilisateur));
        when(encoder.matches("9999", "hashedAncien")).thenReturn(false);

        ChangerPinRequest req = new ChangerPinRequest();
        req.setAncienPin("9999");
        req.setNouveauPin("5678");

        assertThrows(BadRequestException.class, () -> service.changerPin(req, 1L));
        verify(repo, never()).save(any());
    }

    // ── demanderResetPin ──────────────────────────────────────────────────────

    @Test
    void demanderResetPin_envoie_sms() {
        when(repo.findByTelephone("699000001")).thenReturn(Optional.of(utilisateur));
        when(encoder.encode(anyString())).thenReturn("hashedOtp");

        ResetPinRequest req = new ResetPinRequest();
        req.setTelephone("699000001");
        req.setCanal("SMS");

        ApiResponse<String> result = service.demanderResetPin(req);

        assertTrue(result.isSuccess());
        verify(smsAsyncService).envoyerSmsAsync(eq("699000001"), anyString());
        verify(repo).save(argThat(u -> "RESET_PIN".equals(u.getOtpPurpose())));
    }

    @Test
    void demanderResetPin_telephone_inexistant_leve_exception() {
        when(repo.findByTelephone(anyString())).thenReturn(Optional.empty());

        ResetPinRequest req = new ResetPinRequest();
        req.setTelephone("699000000");
        req.setCanal("SMS");

        assertThrows(ResourceNotFoundException.class, () -> service.demanderResetPin(req));
    }

    // ── reinitialiserPin ──────────────────────────────────────────────────────

    @Test
    void reinitialiserPin_succes() {
        utilisateur.setOtpCode("hashedOtp");
        utilisateur.setOtpExpiration(LocalDateTime.now().plusMinutes(5));
        utilisateur.setOtpPurpose("RESET_PIN");

        when(repo.findByTelephone("699000001")).thenReturn(Optional.of(utilisateur));
        when(encoder.matches("654321", "hashedOtp")).thenReturn(true);
        when(encoder.encode("5678")).thenReturn("hashedNouveau");
        when(authHelper.genererAuthResponse(any())).thenReturn(new AuthResponse());

        NouveauPinRequest req = new NouveauPinRequest();
        req.setTelephone("699000001");
        req.setCodeOtp("654321");
        req.setNouveauPin("5678");
        req.setConfirmPin("5678");

        ApiResponse<AuthResponse> result = service.reinitialiserPin(req);

        assertTrue(result.isSuccess());
        verify(repo).save(argThat(u ->
            u.getCodePin().equals("hashedNouveau") &&
            u.getOtpCode() == null &&
            u.getTentativesPinEchouees() == 0
        ));
    }

    @Test
    void reinitialiserPin_otp_expire_leve_exception() {
        utilisateur.setOtpCode("hashedOtp");
        utilisateur.setOtpExpiration(LocalDateTime.now().minusMinutes(1)); // expiré
        utilisateur.setOtpPurpose("RESET_PIN");

        when(repo.findByTelephone("699000001")).thenReturn(Optional.of(utilisateur));
        when(encoder.matches("654321", "hashedOtp")).thenReturn(true);

        NouveauPinRequest req = new NouveauPinRequest();
        req.setTelephone("699000001");
        req.setCodeOtp("654321");
        req.setNouveauPin("5678");
        req.setConfirmPin("5678");

        assertThrows(BadRequestException.class, () -> service.reinitialiserPin(req));
    }
}
