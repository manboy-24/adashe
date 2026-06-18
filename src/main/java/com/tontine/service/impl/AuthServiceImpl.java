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
import com.tontine.service.EmailAsyncService;
import com.tontine.service.NotificationService;
import com.tontine.service.SmsAsyncService;
import com.tontine.util.ContratAdminVersion;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;

@Service @RequiredArgsConstructor @Slf4j @Transactional
public class AuthServiceImpl implements AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final NotificationService notificationService;
    private final SmsAsyncService smsAsyncService;
    private final EmailAsyncService emailAsyncService;
    private final AuditService auditService;
    private final RestTemplate restTemplate;

    @Autowired
    private SecurityUtil securityUtil;

    @Value("${otp.expiration-minutes:5}") private int otpExpiration;
    @Value("${google.client-id}")         private String googleClientId;

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

        boolean viaEmail = request.getEmail() != null && !request.getEmail().isBlank();
        if (viaEmail) {
            emailAsyncService.envoyerEmailAsync(
                request.getEmail(),
                "Adashe — Code de vérification",
                corpsEmailOtp(otp, otpExpiration)
            );
        } else {
            smsAsyncService.envoyerSmsAsync(request.getTelephone(),
                "Adashe - Code de verification : " + otp + ". Valable " + otpExpiration + " min.");
        }

        log.info("Inscription: {} - OTP envoyé par {}", request.getTelephone(), viaEmail ? "email" : "SMS");
        auditService.log(null, request.getTelephone(), "INSCRIPTION", true, null);
        String destination = viaEmail ? masquerEmail(request.getEmail()) : masquerTelephone(request.getTelephone());
        return ApiResponse.success(null, "Code de vérification envoyé à " + destination);
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

        boolean viaEmail = u.getEmail() != null && !u.getEmail().isBlank();
        if (viaEmail) {
            emailAsyncService.envoyerEmailAsync(
                u.getEmail(),
                "Adashe — Nouveau code de vérification",
                corpsEmailOtp(otp, otpExpiration)
            );
        } else {
            smsAsyncService.envoyerSmsAsync(telephone,
                "Adashe - Nouveau code : " + otp + ". Valable " + otpExpiration + " min.");
        }

        String destination = viaEmail ? masquerEmail(u.getEmail()) : masquerTelephone(telephone);
        return ApiResponse.success(null, "Nouveau code envoyé à " + destination);
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
                    "Adashe - Sécurité : activité suspecte détectée. "
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
                        .contratAdminAccepte(ContratAdminVersion.estAcceptee(u.getContratAdminVersion()))
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

    // ── Connexion / inscription via Google ──────────────────────────────────
    @Override
    public ApiResponse<GoogleAuthResponse> connexionGoogle(GoogleAuthRequest request) {
        // Vérifier le token auprès de Google (évite d'ajouter google-api-client)
        Map<String, Object> payload = verifierTokenGoogle(request.getIdToken());

        String email    = (String) payload.get("email");
        String prenom   = (String) payload.getOrDefault("given_name",  "");
        String nom      = (String) payload.getOrDefault("family_name", prenom);
        String nomComplet = (prenom + " " + nom).trim();

        java.util.Optional<Utilisateur> optUser = utilisateurRepository.findByEmail(email);

        if (optUser.isEmpty()) {
            // Nouvel utilisateur — l'app mobile doit demander le numéro de téléphone
            log.info("Connexion Google : nouvel utilisateur {}", email);
            return ApiResponse.success(
                GoogleAuthResponse.builder()
                    .nouvelUtilisateur(true)
                    .email(email)
                    .nomComplet(nomComplet)
                    .build(),
                "Compte non trouvé — veuillez compléter votre inscription"
            );
        }

        // Utilisateur existant → générer les tokens
        Utilisateur u = optUser.get();
        if (!u.getActif()) throw new BadRequestException("Ce compte est désactivé");

        AuthResponse auth = genererAuthResponse(u);
        log.info("Connexion Google réussie : userId={}", u.getId());
        auditService.log(u.getId(), u.getTelephone(), "CONNEXION_GOOGLE", true, null);

        return ApiResponse.success(
            GoogleAuthResponse.builder()
                .nouvelUtilisateur(false)
                .email(email)
                .nomComplet(nomComplet)
                .accessToken(auth.getAccessToken())
                .refreshToken(auth.getRefreshToken())
                .tokenType(auth.getTokenType())
                .expiresIn(auth.getExpiresIn())
                .pinDefini(auth.getPinDefini())
                .utilisateur(auth.getUtilisateur())
                .build(),
            "Connexion réussie"
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> verifierTokenGoogle(String idToken) {
        try {
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            Map<String, Object> payload = restTemplate.getForObject(url, Map.class);
            if (payload == null) throw new BadRequestException("Token Google invalide");

            // Vérifier que l'audience correspond à notre client Web
            String aud = (String) payload.get("aud");
            if (!googleClientId.equals(aud))
                throw new BadRequestException("Token Google invalide (audience incorrecte)");

            String emailVerifie = (String) payload.getOrDefault("email_verified", "false");
            if (!"true".equals(emailVerifie))
                throw new BadRequestException("L'adresse email Google n'est pas vérifiée");

            return payload;
        } catch (HttpClientErrorException e) {
            throw new BadRequestException("Token Google invalide ou expiré");
        }
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

    private String masquerEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        String[] parts = email.split("@");
        String local  = parts[0];
        String domain = parts[1];
        String masque = local.length() <= 2
            ? local.charAt(0) + "***"
            : local.substring(0, 2) + "*".repeat(Math.min(local.length() - 2, 4));
        return masque + "@" + domain;
    }

    private String corpsEmailOtp(String otp, int expirationMinutes) {
        return "Bonjour,\n\n"
            + "Votre code de vérification Adashe est :\n\n"
            + "    " + otp + "\n\n"
            + "Ce code est valable " + expirationMinutes + " minutes.\n\n"
            + "Si vous n'avez pas demandé ce code, ignorez cet email.\n\n"
            + "— L'équipe Adashe";
    }
}