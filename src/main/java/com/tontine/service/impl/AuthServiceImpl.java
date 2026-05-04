package com.tontine.service.impl;

import com.tontine.dto.request.*;
import com.tontine.dto.response.*;
import com.tontine.entity.Utilisateur;
import com.tontine.enums.Role;
import com.tontine.exception.*;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.security.JwtService;
import com.tontine.service.AuditService;
import com.tontine.service.AuthService;
import com.tontine.service.NotificationService;
import com.tontine.service.SmsAsyncService;
import com.tontine.util.OtpUtil;
import com.tontine.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor @Slf4j @Transactional
public class AuthServiceImpl implements AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final NotificationService notificationService;
    private final SmsAsyncService smsAsyncService;
    private final AuditService auditService;

    @Autowired
    private SecurityUtil securityUtil;

    @Value("${otp.expiration-minutes:5}") private int otpExpiration;

    // ── Inscription : téléphone + nom/prénom, pas de mot de passe ─────────────
    @Override
    public ApiResponse<String> inscrire(InscriptionRequest request) {
        if (utilisateurRepository.existsByTelephone(request.getTelephone()))
            throw new BadRequestException("Ce numéro est déjà utilisé");
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && utilisateurRepository.existsByEmail(request.getEmail()))
            throw new BadRequestException("Cet email est déjà utilisé");

        String otp = OtpUtil.generer(6);
        Utilisateur u = Utilisateur.builder()
                .nom(request.getNom()).prenom(request.getPrenom())
                .telephone(request.getTelephone()).email(request.getEmail())
                .role(Role.USER)
                .otpCode(passwordEncoder.encode(otp))
                .otpExpiration(LocalDateTime.now().plusMinutes(otpExpiration))
                .otpPurpose("INSCRIPTION")
                .build();
        utilisateurRepository.save(u);

        smsAsyncService.envoyerSmsAsync(request.getTelephone(),
                "AdasheCash - Code de verification : " + otp + ". Valable " + otpExpiration + " min.");

        log.info("Inscription: {} - OTP envoye", request.getTelephone());
        auditService.log(null, request.getTelephone(), "INSCRIPTION", true, null);
        return ApiResponse.success(null,
                "Code de vérification envoyé au " + masquerTelephone(request.getTelephone()));
    }

    // ── Vérifier OTP d'inscription ──────────────────────────────────────────
    @Override
    public ApiResponse<AuthResponse> verifierOtp(OtpRequest request) {
        Utilisateur u = utilisateurRepository.findByTelephone(request.getTelephone())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        if (u.getOtpCode() == null || !passwordEncoder.matches(request.getCode(), u.getOtpCode()))
            throw new BadRequestException("Code OTP invalide");
        if (LocalDateTime.now().isAfter(u.getOtpExpiration()))
            throw new BadRequestException("Code OTP expiré. Veuillez en demander un nouveau.");

        u.setTelephoneVerifie(true);
        u.setOtpCode(null);
        u.setOtpExpiration(null);
        u.setOtpPurpose(null);
        utilisateurRepository.save(u);

        // Retourne un JWT temporaire pour permettre la création du PIN
        log.info("OTP vérifié pour: {}", request.getTelephone());
        auditService.log(u.getId(), u.getTelephone(), "OTP_VERIFIE", true, null);
        AuthResponse authResponse = genererAuthResponse(u);
        return ApiResponse.success(authResponse, "Téléphone vérifié ! Créez maintenant votre PIN à 4 chiffres.");
    }

    // ── Renvoyer OTP ────────────────────────────────────────────────────────
    @Override
    public ApiResponse<String> renvoyerOtp(String telephone) {
        Utilisateur u = utilisateurRepository.findByTelephone(telephone)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // Rate limit : bloquer pendant toute la durée de validité de l'OTP en cours
        if (u.getOtpExpiration() != null && LocalDateTime.now().isBefore(u.getOtpExpiration())) {
            long secondes = java.time.Duration.between(LocalDateTime.now(), u.getOtpExpiration()).getSeconds();
            throw new BadRequestException("Code déjà envoyé. Attendez " + secondes + " secondes avant de redemander.");
        }

        String otp = OtpUtil.generer(6);
        u.setOtpCode(passwordEncoder.encode(otp));
        u.setOtpExpiration(LocalDateTime.now().plusMinutes(otpExpiration));
        utilisateurRepository.save(u);

        smsAsyncService.envoyerSmsAsync(telephone,
                "AdasheCash - Nouveau code : " + otp + ". Valable " + otpExpiration + " min.");

        return ApiResponse.success(null, "Nouveau code envoyé au " + masquerTelephone(telephone));
    }

    // ── Refresh token JWT ───────────────────────────────────────────────────
    @Override
    public ApiResponse<AuthResponse> rafraichirToken(String refreshToken) {
        String hash = hashToken(refreshToken);

        // Cas normal : token actif trouvé
        java.util.Optional<Utilisateur> optUser = utilisateurRepository.findByRefreshToken(hash);
        if (optUser.isPresent()) {
            Utilisateur u = optUser.get();
            if (LocalDateTime.now().isAfter(u.getRefreshTokenExpiration())) {
                u.setRefreshToken(null);
                u.setRefreshTokenExpiration(null);
                u.setPreviousRefreshTokenHash(null);
                utilisateurRepository.save(u);
                auditService.log(u.getId(), u.getTelephone(), "REFRESH_TOKEN", false, "Token expiré");
                throw new UnauthorizedException("Session expirée. Veuillez vous reconnecter.");
            }
            auditService.log(u.getId(), u.getTelephone(), "REFRESH_TOKEN", true, null);
            return ApiResponse.success(genererAuthResponse(u), "Token rafraîchi");
        }

        // Replay détecté : le token correspond au précédent déjà consommé → vol potentiel
        utilisateurRepository.findByPreviousRefreshTokenHash(hash).ifPresent(u -> {
            log.warn("SECURITY — Replay token détecté pour userId={} tel={}", u.getId(), u.getTelephone());
            u.setRefreshToken(null);
            u.setRefreshTokenExpiration(null);
            u.setPreviousRefreshTokenHash(null);
            u.setFcmToken(null);
            utilisateurRepository.save(u);
            auditService.log(u.getId(), u.getTelephone(), "TOKEN_REPLAY", false,
                    "Toutes les sessions invalidées — replay détecté");
            smsAsyncService.envoyerSmsAsync(u.getTelephone(),
                    "AdasheCash - Sécurité : activité suspecte détectée. "
                    + "Toutes vos sessions ont été déconnectées. "
                    + "Reconnectez-vous et changez votre PIN si ce n'était pas vous.");
        });

        throw new UnauthorizedException("Session invalide. Veuillez vous reconnecter.");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    public AuthResponse genererAuthResponse(Utilisateur u) {
        UserDetails ud = userDetailsService.loadUserByUsername(u.getTelephone());
        String access  = jwtService.generateToken(ud);
        String refresh = jwtService.generateRefreshToken(ud);

        // Conserver le hash actuel comme "précédent" pour détecter les replays
        u.setPreviousRefreshTokenHash(u.getRefreshToken());
        u.setRefreshToken(hashToken(refresh));
        u.setRefreshTokenExpiration(LocalDateTime.now().plusDays(7));
        utilisateurRepository.save(u);

        return AuthResponse.builder()
                .accessToken(access).refreshToken(refresh)
                .tokenType("Bearer").expiresIn(jwtService.getExpirationTime())
                .pinDefini(u.getPinDefini() != null && u.getPinDefini())
                .utilisateur(UtilisateurResponse.builder()
                        .id(u.getId()).nom(u.getNom()).prenom(u.getPrenom())
                        .telephone(u.getTelephone()).email(u.getEmail())
                        .avatarId(u.getAvatarId())
                        .telephoneVerifie(u.getTelephoneVerifie())
                        .pinDefini(u.getPinDefini() != null && u.getPinDefini())
                        .createdAt(u.getCreatedAt()).build())
                .build();
    }

    // ── Déconnexion : invalide le refresh token en base ─────────────────────
    @Override
    public ApiResponse<String> deconnecter(Long userId) {
        Long currentUserId = securityUtil.getCurrentUserId();
        if (!currentUserId.equals(userId)) {
            throw new ForbiddenException("Vous ne pouvez vous déconnecter que de votre propre compte");
        }
        utilisateurRepository.findById(userId).ifPresent(u -> {
            u.setRefreshToken(null);
            u.setRefreshTokenExpiration(null);
            u.setFcmToken(null);
            utilisateurRepository.save(u);
            log.info("Déconnexion: userId={}", userId);
            auditService.log(u.getId(), u.getTelephone(), "DECONNEXION", true, null);
        });
        return ApiResponse.success(null, "Déconnecté avec succès");
    }

    /** SHA-256 du token — stocké en base, jamais le JWT brut. */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erreur hachage token", e);
        }
    }

    private String masquerTelephone(String tel) {
        if (tel == null || tel.length() < 4) return "****";
        return tel.substring(0, tel.length() - 4).replaceAll("\\d", "*") + tel.substring(tel.length() - 4);
    }
}