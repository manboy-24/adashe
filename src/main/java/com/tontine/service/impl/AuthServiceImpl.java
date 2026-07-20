package com.tontine.service.impl;

import com.tontine.dto.request.*;
import com.tontine.dto.response.*;
import com.tontine.entity.Session;
import com.tontine.entity.Utilisateur;
import com.tontine.enums.Role;
import com.tontine.exception.*;
import com.tontine.repository.SessionRepository;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.security.JwtService;
import com.tontine.service.AuditService;
import com.tontine.service.AuthService;
import com.tontine.service.EmailAsyncService;
import com.tontine.service.NotificationService;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;

@Service @RequiredArgsConstructor @Slf4j @Transactional
public class AuthServiceImpl implements AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final NotificationService notificationService;
    private final EmailAsyncService emailAsyncService;
    private final AuditService auditService;
    private final RestTemplate restTemplate;

    @Autowired
    private SecurityUtil securityUtil;

    @Value("${otp.expiration-minutes:5}") private int otpExpiration;
    @Value("${google.client-id}")         private String googleClientId;

    // ── Inscription : téléphone + nom/prénom, compte actif immédiatement ────────
    @Override
    public ApiResponse<AuthResponse> inscrire(InscriptionRequest request) {
        if (utilisateurRepository.existsByTelephone(request.getTelephone()))
            throw new BadRequestException("Ce numéro est déjà utilisé");
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && utilisateurRepository.existsByEmail(request.getEmail()))
            throw new BadRequestException("Cet email est déjà utilisé");

        Utilisateur u = Utilisateur.builder()
                .nom(request.getNom()).prenom(request.getPrenom())
                .telephone(request.getTelephone()).email(request.getEmail())
                .role(Role.USER)
                .telephoneVerifie(true)
                .codePin(passwordEncoder.encode(request.getPin()))
                .pinDefini(true)
                .tentativesPinEchouees(0)
                .build();
        utilisateurRepository.save(u);

        log.info("Inscription: {}", request.getTelephone());
        auditService.log(u.getId(), request.getTelephone(), "INSCRIPTION", true, null);
        AuthResponse authResponse = genererAuthResponse(u);
        return ApiResponse.success(authResponse, "Compte créé avec succès !");
    }

    // ── Inscription via Firebase Phone Auth ────────────────────────────────
    @Override
    public ApiResponse<AuthResponse> inscrireAvecFirebase(FirebaseInscriptionRequest request) {
        String telephone = verifierTokenFirebaseTelephone(request.getIdToken());

        if (utilisateurRepository.existsByTelephone(telephone))
            throw new BadRequestException("Ce numéro est déjà utilisé");
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && utilisateurRepository.existsByEmail(request.getEmail()))
            throw new BadRequestException("Cet email est déjà utilisé");

        Utilisateur u = Utilisateur.builder()
                .nom(request.getNom()).prenom(request.getPrenom())
                .telephone(telephone).email(request.getEmail())
                .role(Role.USER)
                .telephoneVerifie(true)
                .codePin(passwordEncoder.encode(request.getPin()))
                .pinDefini(true)
                .tentativesPinEchouees(0)
                .build();
        utilisateurRepository.save(u);

        log.info("Inscription Firebase: {}", telephone);
        auditService.log(u.getId(), telephone, "INSCRIPTION_FIREBASE", true, null);
        return ApiResponse.success(genererAuthResponse(u), "Compte créé avec succès !");
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

        // L'OTP part par email uniquement (plus de SMS) — l'email est obligatoire
        // à l'inscription ; les comptes sans email passent par Firebase Phone Auth
        if (u.getEmail() == null || u.getEmail().isBlank()) {
            throw new BadRequestException(
                    "Aucun email associé à ce compte. Utilisez la vérification par téléphone (Firebase).");
        }
        emailAsyncService.envoyerEmailAsync(
            u.getEmail(),
            "Adashe — Nouveau code de vérification",
            corpsEmailOtp(otp, otpExpiration)
        );

        return ApiResponse.success(null, "Nouveau code envoyé à " + masquerEmail(u.getEmail()));
    }

    // ── Refresh token JWT ───────────────────────────────────────────────────
    @Override
    public ApiResponse<AuthResponse> rafraichirToken(String refreshToken) {
        String hash = hashToken(refreshToken);

        // ── 1. Vérifier dans la table sessions (connexions avec deviceId) ───────
        java.util.Optional<Session> optSession = sessionRepository.findByRefreshTokenHash(hash);
        if (optSession.isPresent()) {
            Session session = optSession.get();
            Utilisateur u   = session.getUtilisateur();
            if (!Boolean.TRUE.equals(session.getActive()) || LocalDateTime.now().isAfter(session.getExpiresAt())) {
                session.setActive(false);
                sessionRepository.save(session);
                auditService.log(u.getId(), u.getTelephone(), "REFRESH_TOKEN", false, "Session expirée");
                throw new UnauthorizedException("Session expirée. Veuillez vous reconnecter.");
            }
            auditService.log(u.getId(), u.getTelephone(), "REFRESH_TOKEN", true, "device: " + session.getDeviceName());
            return ApiResponse.success(genererAuthResponseAvecSession(u, session.getDeviceId(), session.getDeviceName()), "Token rafraîchi");
        }

        // ── 2. Replay dans sessions (token déjà consommé) ────────────────────────
        sessionRepository.findByPreviousRefreshTokenHash(hash).ifPresent(session -> {
            Utilisateur u = session.getUtilisateur();
            log.warn("SECURITY — Replay session={} userId={}", session.getId(), u.getId());
            sessionRepository.deactivateAll(u.getId());
            u.setRefreshToken(null); u.setRefreshTokenExpiration(null); u.setPreviousRefreshTokenHash(null);
            utilisateurRepository.save(u);
            auditService.log(u.getId(), u.getTelephone(), "TOKEN_REPLAY", false, "Toutes sessions révoquées");
            // Sessions coupées → le push n'arriverait pas ; l'email est le canal fiable ici
            envoyerAlerteActiviteSuspecte(u);
        });

        // ── 3. Fallback legacy : token stocké sur l'utilisateur (sans deviceId) ──
        java.util.Optional<Utilisateur> optUser = utilisateurRepository.findByRefreshToken(hash);
        if (optUser.isPresent()) {
            Utilisateur u = optUser.get();
            if (LocalDateTime.now().isAfter(u.getRefreshTokenExpiration())) {
                u.setRefreshToken(null); u.setRefreshTokenExpiration(null); u.setPreviousRefreshTokenHash(null);
                utilisateurRepository.save(u);
                auditService.log(u.getId(), u.getTelephone(), "REFRESH_TOKEN", false, "Token expiré (legacy)");
                throw new UnauthorizedException("Session expirée. Veuillez vous reconnecter.");
            }
            auditService.log(u.getId(), u.getTelephone(), "REFRESH_TOKEN", true, "legacy");
            return ApiResponse.success(genererAuthResponse(u), "Token rafraîchi");
        }

        // ── 4. Replay legacy ──────────────────────────────────────────────────────
        utilisateurRepository.findByPreviousRefreshTokenHash(hash).ifPresent(u -> {
            log.warn("SECURITY — Replay token legacy userId={}", u.getId());
            u.setRefreshToken(null); u.setRefreshTokenExpiration(null); u.setPreviousRefreshTokenHash(null);
            u.setFcmToken(null);
            utilisateurRepository.save(u);
            sessionRepository.deactivateAll(u.getId());
            auditService.log(u.getId(), u.getTelephone(), "TOKEN_REPLAY", false, "Legacy — sessions révoquées");
            envoyerAlerteActiviteSuspecte(u);
        });

        throw new UnauthorizedException("Session invalide. Veuillez vous reconnecter.");
    }

    private void envoyerAlerteActiviteSuspecte(Utilisateur u) {
        if (u.getEmail() == null || u.getEmail().isBlank()) return;
        emailAsyncService.envoyerEmailAsync(u.getEmail(),
                "Sécurité Adashe — activité suspecte détectée",
                "Bonjour " + u.getPrenom() + ",\n\n"
                + "Une activité suspecte a été détectée sur votre compte. "
                + "Toutes vos sessions ont été déconnectées par précaution.\n"
                + "Reconnectez-vous et changez votre PIN si ce n'était pas vous.\n\n"
                + "— L'équipe Adashe");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Génère un AuthResponse sans créer de session (flows sans deviceId). */
    public AuthResponse genererAuthResponse(Utilisateur u) {
        UserDetails ud = userDetailsService.loadUserByUsername(u.getTelephone());
        String access  = jwtService.generateToken(ud);
        String refresh = jwtService.generateRefreshToken(ud);

        u.setPreviousRefreshTokenHash(u.getRefreshToken());
        u.setRefreshToken(hashToken(refresh));
        u.setRefreshTokenExpiration(LocalDateTime.now().plusDays(7));
        utilisateurRepository.save(u);

        return buildAuthResponse(u, access, refresh);
    }

    /** Génère un AuthResponse ET crée/met à jour la session pour le deviceId donné. */
    public AuthResponse genererAuthResponseAvecSession(Utilisateur u, String deviceId, String deviceName) {
        UserDetails ud = userDetailsService.loadUserByUsername(u.getTelephone());
        String access  = jwtService.generateToken(ud);
        String refresh = jwtService.generateRefreshToken(ud);
        String hash    = hashToken(refresh);

        Session session = sessionRepository
                .findByUtilisateurIdAndDeviceIdAndActiveTrue(u.getId(), deviceId)
                .orElse(Session.builder()
                        .utilisateur(u)
                        .deviceId(deviceId)
                        .deviceName(deviceName)
                        .active(true)
                        .build());
        session.setPreviousRefreshTokenHash(session.getRefreshTokenHash());
        session.setRefreshTokenHash(hash);
        session.setLastUsedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(7));
        session.setActive(true);
        sessionRepository.save(session);

        return buildAuthResponse(u, access, refresh);
    }

    private AuthResponse buildAuthResponse(Utilisateur u, String access, String refresh) {
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

    // ── Déconnexion : invalide le refresh token et toutes les sessions ───────
    @Override
    public ApiResponse<String> deconnecter(Long userId) {
        utilisateurRepository.findById(userId).ifPresent(u -> {
            u.setRefreshToken(null);
            u.setRefreshTokenExpiration(null);
            u.setFcmToken(null);
            utilisateurRepository.save(u);
            sessionRepository.deactivateAll(userId);
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

    @Override
    public boolean telephoneExiste(String telephone) {
        return utilisateurRepository.existsByTelephone(telephone);
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

    // ── Helpers Firebase ─────────────────────────────────────────────────────

    /** Vérifie un ID token Firebase et retourne le numéro de téléphone local (sans +237). */
    public String verifierTokenFirebaseTelephone(String idToken) {
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String phone = (String) decoded.getClaims().get("phone_number");
            if (phone == null || phone.isBlank())
                throw new BadRequestException("Le token Firebase ne contient pas de numéro vérifié");
            return normaliserTelephoneE164(phone);
        } catch (FirebaseAuthException e) {
            log.warn("Token Firebase invalide: {}", e.getMessage());
            throw new BadRequestException("Token Firebase invalide ou expiré");
        }
    }

    /** Convertit +237XXXXXXXXX → XXXXXXXXX (format stocké en base). */
    private String normaliserTelephoneE164(String e164) {
        if (e164.startsWith("+237")) return e164.substring(4);
        if (e164.startsWith("+"))   return e164.substring(1);
        return e164;
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