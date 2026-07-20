package com.tontine.service.impl;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.tontine.dto.request.*;
import com.tontine.dto.response.*;
import com.tontine.entity.Session;
import com.tontine.entity.Utilisateur;
import com.tontine.exception.*;
import com.tontine.repository.SessionRepository;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.security.JwtService;
import com.tontine.service.*;
import com.tontine.service.AuditService;
import com.tontine.service.EmailAsyncService;
import com.tontine.service.PushAsyncService;
import com.tontine.service.SmsAsyncService;
import com.tontine.util.OtpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Slf4j @Transactional
public class PinAuthServiceImpl implements PinAuthService {

    private final UtilisateurRepository repo;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final NotificationService notifService;
    private final SmsAsyncService smsAsyncService;
    private final EmailAsyncService emailAsyncService;
    private final PushAsyncService pushAsyncService;
    private final AuthServiceImpl authHelper;
    private final AuditService auditService;

    @Value("${otp.expiration-minutes:5}")   private int otpExpiration;
    @Value("${pin.max-tentatives:5}")        private int maxTentatives;
    @Value("${pin.blocage-minutes:15}")      private int blocageMinutes;

    // ── 1. Créer un PIN (après vérification OTP) ──────────────────────────────
    @Override
    public ApiResponse<AuthResponse> creerPin(CreationPinRequest request, Long userId) {
        if (!request.getPin().equals(request.getConfirmPin()))
            throw new BadRequestException("Les deux PINs ne correspondent pas");
        if (!request.getPin().matches("\\d{4}"))
            throw new BadRequestException("Le PIN doit contenir exactement 4 chiffres");

        Utilisateur u = repo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        if (!Boolean.TRUE.equals(u.getTelephoneVerifie()))
            throw new BadRequestException("Vous devez vérifier votre téléphone avant de créer un PIN");

        u.setCodePin(encoder.encode(request.getPin()));
        u.setPinDefini(true);
        u.setTentativesPinEchouees(0);
        repo.save(u);

        log.info("PIN créé pour: {}", u.getTelephone());
        auditService.log(u.getId(), u.getTelephone(), "CREATION_PIN", true, null);
        // Connexion automatique après création du PIN
        return ApiResponse.success(authHelper.genererAuthResponse(u), "PIN créé avec succès ! Bienvenue sur Tontine+");
    }

    // ── 2. Connexion téléphone + PIN ───────────────────────────────────────────
    @Override
    public ApiResponse<ConnexionPinResponse> connecterAvecPin(ConnexionPinRequest request) {
        // On ne révèle pas si le numéro existe ou non (sécurité)
        Utilisateur u = repo.findByTelephone(request.getTelephone())
                .orElseThrow(() -> new UnauthorizedException("Numéro ou PIN incorrect"));

        if (!Boolean.TRUE.equals(u.getActif()))
            throw new UnauthorizedException("Ce compte est désactivé");

        if (!Boolean.TRUE.equals(u.getPinDefini()) || u.getCodePin() == null)
            throw new BadRequestException("Aucun PIN configuré. Veuillez créer votre PIN après inscription.");

        // Vérifier blocage
        if (u.estPinBloque()) {
            long restant = java.time.Duration.between(LocalDateTime.now(), u.getPinBloqueJusquA()).toMinutes();
            throw new UnauthorizedException(
                "Compte temporairement bloqué. Réessayez dans " + (restant + 1) + " minute(s) ou réinitialisez votre PIN.");
        }

        if (!encoder.matches(request.getPin(), u.getCodePin())) {
            repo.incrementTentativesEchouees(u.getId());
            u = repo.findById(u.getId()).orElseThrow();
            int tentatives = u.getTentativesPinEchouees() == null ? 1 : u.getTentativesPinEchouees();
            int restantes  = maxTentatives - tentatives;

            if (tentatives >= maxTentatives) {
                u.setPinBloqueJusquA(LocalDateTime.now().plusMinutes(blocageMinutes));
                repo.save(u);
                auditService.log(u.getId(), u.getTelephone(), "CONNEXION_PIN", false,
                        "Compte bloqué après " + tentatives + " tentatives");
                smsAsyncService.envoyerSmsAsync(u.getTelephone(),
                        "Adashe - Sécurité : votre compte a été bloqué " + blocageMinutes
                        + " min après " + tentatives + " tentatives incorrectes. "
                        + "Si ce n'était pas vous, réinitialisez votre PIN immédiatement.");
                throw new UnauthorizedException(
                    maxTentatives + " tentatives échouées. Compte bloqué " + blocageMinutes
                    + " min. Utilisez 'Oublié mon PIN' pour réinitialiser.");
            }
            auditService.log(u.getId(), u.getTelephone(), "CONNEXION_PIN", false,
                    "Tentative " + tentatives + "/" + maxTentatives);
            throw new UnauthorizedException("PIN incorrect. " + restantes + " tentative(s) restante(s).");
        }

        // PIN valide
        u.setTentativesPinEchouees(0);
        u.setPinBloqueJusquA(null);
        repo.save(u);

        // ── Vérification d'appareil ──────────────────────────────────────────
        String deviceId   = request.getDeviceId();
        String deviceName = request.getDeviceName();

        if (deviceId != null && !deviceId.isBlank()) {
            boolean appareilConnu = sessionRepository
                    .existsByUtilisateurIdAndDeviceIdAndActiveTrue(u.getId(), deviceId);

            if (!appareilConnu) {
                List<Session> sessionsActives = sessionRepository
                        .findByUtilisateurIdAndActiveTrue(u.getId());

                if (!sessionsActives.isEmpty()) {
                    // Appareil inconnu + sessions existantes → OTP requis
                    String otp = OtpUtil.generer(6);
                    u.setOtpCode(encoder.encode(otp));
                    u.setOtpExpiration(LocalDateTime.now().plusMinutes(otpExpiration));
                    u.setOtpPurpose("NOUVEL_APPAREIL");
                    repo.save(u);

                    // Notifier les appareils existants via push
                    sessionsActives.stream()
                            .filter(s -> s.getFcmToken() != null && !s.getFcmToken().isBlank())
                            .forEach(s -> pushAsyncService.envoyerPushAsync(
                                    s.getFcmToken(),
                                    "Tentative de connexion sur un nouvel appareil",
                                    "Si ce n'est pas vous, sécurisez votre compte immédiatement.",
                                    "SECURITE",
                                    null));

                    String destination;
                    boolean viaEmail = u.getEmail() != null && !u.getEmail().isBlank();
                    if (viaEmail) {
                        notifService.envoyerEmail(u.getEmail(),
                                "Adashe — Connexion sur un nouvel appareil",
                                "Bonjour " + u.getPrenom() + ",\n\n"
                                + "Une tentative de connexion a été détectée depuis un nouvel appareil"
                                + (deviceName != null ? " (" + deviceName + ")" : "") + ".\n\n"
                                + "Code de confirmation : " + otp + "\n"
                                + "Valable " + otpExpiration + " minutes.\n\n"
                                + "Si ce n'est pas vous, changez votre PIN immédiatement.\n\n"
                                + "— L'équipe Adashe");
                        destination = masquerEmail(u.getEmail());
                    } else {
                        smsAsyncService.envoyerSmsAsync(u.getTelephone(),
                                "Adashe - Code appareil : " + otp + ". Valable " + otpExpiration
                                + " min. Si ce n'est pas vous, changez votre PIN.");
                        destination = masquerTelephone(u.getTelephone());
                    }

                    log.info("Nouvel appareil détecté userId={} deviceId={}", u.getId(), deviceId);
                    auditService.log(u.getId(), u.getTelephone(), "NOUVEL_APPAREIL", false,
                            "deviceId=" + deviceId);

                    return ApiResponse.success(
                            ConnexionPinResponse.builder()
                                    .action("NOUVEL_APPAREIL_OTP")
                                    .otpDestination(destination)
                                    .build(),
                            "Nouvel appareil détecté. Code envoyé à " + destination);
                }
            }
            // Appareil connu OU premier appareil (aucune session active) → session
            AuthResponse auth = authHelper.genererAuthResponseAvecSession(u, deviceId, deviceName);
            log.info("Connexion PIN réussie: {} device={}", u.getTelephone(), deviceName);
            auditService.log(u.getId(), u.getTelephone(), "CONNEXION_PIN", true, "device=" + deviceName);
            return ApiResponse.success(toConnexionPinResponse(auth), "Connexion réussie");
        }

        // Pas de deviceId → chemin legacy
        AuthResponse auth = authHelper.genererAuthResponse(u);
        log.info("Connexion PIN réussie (legacy): {}", u.getTelephone());
        auditService.log(u.getId(), u.getTelephone(), "CONNEXION_PIN", true, null);
        return ApiResponse.success(toConnexionPinResponse(auth), "Connexion réussie");
    }

    // ── 2b. Confirmer connexion depuis nouvel appareil (OTP) ─────────────────
    @Override
    public ApiResponse<ConnexionPinResponse> confirmerNouvelAppareil(ConfirmerNouvelAppareilRequest request) {
        Utilisateur u = repo.findByTelephone(request.getTelephone())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        if (!Boolean.TRUE.equals(u.getPinDefini()) || u.getCodePin() == null
                || !encoder.matches(request.getPin(), u.getCodePin()))
            throw new UnauthorizedException("PIN incorrect");

        if (!"NOUVEL_APPAREIL".equals(u.getOtpPurpose()) || u.getOtpCode() == null)
            throw new BadRequestException("Aucune demande de confirmation en cours");

        if (!encoder.matches(request.getOtpCode(), u.getOtpCode()))
            throw new BadRequestException("Code de vérification invalide");

        if (LocalDateTime.now().isAfter(u.getOtpExpiration()))
            throw new BadRequestException("Code expiré. Veuillez vous reconnecter pour en recevoir un nouveau.");

        u.setOtpCode(null);
        u.setOtpExpiration(null);
        u.setOtpPurpose(null);
        repo.save(u);

        AuthResponse auth = authHelper.genererAuthResponseAvecSession(
                u, request.getDeviceId(), request.getDeviceName());

        log.info("Nouvel appareil confirmé userId={} deviceId={}", u.getId(), request.getDeviceId());
        auditService.log(u.getId(), u.getTelephone(), "CONFIRMER_NOUVEL_APPAREIL", true,
                "deviceId=" + request.getDeviceId());

        return ApiResponse.success(toConnexionPinResponse(auth), "Appareil confirmé ! Connexion réussie.");
    }

    // ── 3. Demander reset PIN (OTP envoyé uniquement par email) ──────────────
    @Override
    public ApiResponse<String> demanderResetPin(ResetPinRequest request) {
        Utilisateur u = repo.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Aucun compte trouvé avec cet email"));

        if (u.getEmail() == null || u.getEmail().isBlank())
            throw new BadRequestException("Aucun email associé à ce compte");

        // Rate limit : un seul code actif à la fois
        if (u.getOtpExpiration() != null && LocalDateTime.now().isBefore(u.getOtpExpiration().minusSeconds(30))) {
            long secondes = java.time.Duration.between(LocalDateTime.now(), u.getOtpExpiration()).getSeconds();
            throw new BadRequestException("Code déjà envoyé. Attendez " + secondes + " secondes avant de redemander.");
        }

        String otp = OtpUtil.generer(6);
        u.setOtpCode(encoder.encode(otp));
        u.setOtpExpiration(LocalDateTime.now().plusMinutes(otpExpiration));
        u.setOtpPurpose("RESET_PIN");
        repo.save(u);

        notifService.envoyerEmail(u.getEmail(),
                "Réinitialisation de votre code PIN Adashe",
                "Bonjour " + u.getPrenom() + ",\n\n"
                + "Votre code de réinitialisation : " + otp + "\n"
                + "Valable " + otpExpiration + " minutes.\n\n"
                + "Si vous n'avez pas fait cette demande, ignorez cet email.\n\n"
                + "L'équipe Adashe");

        return ApiResponse.success(null, "Code envoyé à " + masquerEmail(u.getEmail()));
    }

    // ── 4. Confirmer reset PIN avec OTP + nouveau PIN ─────────────────────────
    @Override
    public ApiResponse<AuthResponse> reinitialiserPin(NouveauPinRequest request) {
        if (!request.getNouveauPin().equals(request.getConfirmPin()))
            throw new BadRequestException("Les deux PINs ne correspondent pas");
        if (!request.getNouveauPin().matches("\\d{4}"))
            throw new BadRequestException("Le PIN doit contenir exactement 4 chiffres");

        Utilisateur u = repo.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        if (u.getOtpCode() == null || !encoder.matches(request.getCodeOtp(), u.getOtpCode()))
            throw new BadRequestException("Code OTP invalide");
        if (LocalDateTime.now().isAfter(u.getOtpExpiration()))
            throw new BadRequestException("Code OTP expiré. Veuillez en demander un nouveau.");
        if (!"RESET_PIN".equals(u.getOtpPurpose()))
            throw new BadRequestException("Ce code n'est pas valable pour le reset PIN");

        u.setCodePin(encoder.encode(request.getNouveauPin()));
        u.setPinDefini(true);
        u.setOtpCode(null); u.setOtpExpiration(null); u.setOtpPurpose(null);
        u.setTentativesPinEchouees(0);
        u.setPinBloqueJusquA(null);
        // Invalider toutes les sessions existantes
        u.setRefreshToken(null);
        u.setRefreshTokenExpiration(null);
        repo.save(u);

        log.info("PIN réinitialisé pour: {}", u.getTelephone());
        auditService.log(u.getId(), u.getTelephone(), "RESET_PIN", true, null);
        if (u.getEmail() != null && !u.getEmail().isBlank()) {
            emailAsyncService.envoyerEmailAsync(u.getEmail(),
                    "Sécurité Adashe — votre PIN a été réinitialisé",
                    "Bonjour " + u.getPrenom() + ",\n\n"
                    + "Votre PIN Adashe a été réinitialisé avec succès.\n"
                    + "Si vous n'êtes pas à l'origine de cette action, "
                    + "contactez-nous immédiatement.\n\n"
                    + "L'équipe Adashe");
        }
        return ApiResponse.success(authHelper.genererAuthResponse(u),
                "PIN réinitialisé avec succès ! Vous êtes maintenant connecté.");
    }

    // ── 4b. Reset PIN via Firebase Phone Auth (ou Email Link) ────────────────
    @Override
    public ApiResponse<AuthResponse> reinitialiserPinFirebase(FirebasePinResetRequest request) {
        if (!request.getNouveauPin().matches("\\d{4}"))
            throw new BadRequestException("Le PIN doit contenir exactement 4 chiffres");

        FirebaseToken decoded;
        try {
            decoded = FirebaseAuth.getInstance().verifyIdToken(request.getIdToken());
        } catch (FirebaseAuthException e) {
            log.warn("Token Firebase invalide pour reset PIN: {}", e.getMessage());
            throw new BadRequestException("Token Firebase invalide ou expiré");
        }

        Utilisateur u;
        String phoneRaw = (String) decoded.getClaims().get("phone_number");
        String email    = decoded.getEmail();

        if (phoneRaw != null && !phoneRaw.isBlank()) {
            String telephone = normaliserTelephoneE164(phoneRaw);
            u = repo.findByTelephone(telephone)
                    .orElseThrow(() -> new ResourceNotFoundException("Aucun compte trouvé avec ce numéro"));
        } else if (email != null && !email.isBlank()) {
            u = repo.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("Aucun compte trouvé avec cet email"));
        } else {
            throw new BadRequestException("Token Firebase invalide : ni téléphone ni email");
        }

        u.setCodePin(encoder.encode(request.getNouveauPin()));
        u.setPinDefini(true);
        u.setTentativesPinEchouees(0);
        u.setPinBloqueJusquA(null);
        u.setRefreshToken(null);
        u.setRefreshTokenExpiration(null);
        repo.save(u);

        log.info("PIN réinitialisé via Firebase pour: {}", u.getTelephone());
        auditService.log(u.getId(), u.getTelephone(), "RESET_PIN_FIREBASE", true, null);

        if (u.getEmail() != null && !u.getEmail().isBlank()) {
            emailAsyncService.envoyerEmailAsync(u.getEmail(),
                    "Sécurité Adashe — votre PIN a été réinitialisé",
                    "Bonjour " + u.getPrenom() + ",\n\nVotre PIN Adashe a été réinitialisé.\n"
                    + "Si vous n'êtes pas à l'origine de cette action, contactez-nous immédiatement.\n\n"
                    + "— L'équipe Adashe");
        }
        return ApiResponse.success(authHelper.genererAuthResponse(u), "PIN réinitialisé avec succès !");
    }

    private String normaliserTelephoneE164(String e164) {
        if (e164.startsWith("+237")) return e164.substring(4);
        if (e164.startsWith("+"))   return e164.substring(1);
        return e164;
    }

    private String masquerTelephone(String tel) {
        if (tel == null || tel.length() < 4) return "****";
        return tel.substring(0, tel.length() - 4).replaceAll("\\d", "*") + tel.substring(tel.length() - 4);
    }

    private String masquerEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        String[] p = email.split("@");
        String local = p[0];
        return (local.length() > 2 ? local.substring(0, 2) : local) + "****@" + p[1];
    }

    // ── 5. Changer son PIN (utilisateur connecté) ─────────────────────────────
    @Override
    public ApiResponse<String> changerPin(ChangerPinRequest request, Long userId) {
        Utilisateur u = repo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        if (u.getCodePin() == null || !encoder.matches(request.getAncienPin(), u.getCodePin()))
            throw new BadRequestException("Ancien PIN incorrect");

        if (!request.getNouveauPin().matches("\\d{4}"))
            throw new BadRequestException("Le nouveau PIN doit contenir exactement 4 chiffres");

        u.setCodePin(encoder.encode(request.getNouveauPin()));
        u.setTentativesPinEchouees(0);
        u.setPinBloqueJusquA(null);
        // Invalider les autres sessions actives (sécurité : changement de PIN = déco forcée)
        u.setRefreshToken(null);
        u.setRefreshTokenExpiration(null);
        repo.save(u);

        log.info("PIN changé pour: {}", u.getTelephone());
        auditService.log(u.getId(), u.getTelephone(), "CHANGEMENT_PIN", true, null);
        if (u.getEmail() != null && !u.getEmail().isBlank()) {
            emailAsyncService.envoyerEmailAsync(u.getEmail(),
                    "Sécurité Adashe — votre PIN a été modifié",
                    "Bonjour " + u.getPrenom() + ",\n\n"
                    + "Votre PIN Adashe a été modifié avec succès.\n"
                    + "Si vous n'êtes pas à l'origine de cette modification, "
                    + "réinitialisez immédiatement votre PIN via l'application.\n\n"
                    + "L'équipe Adashe");
        }
        return ApiResponse.success(null, "PIN modifié avec succès. Veuillez vous reconnecter.");
    }

    @Override
    public ApiResponse<String> verifierPin(String pin, Long userId) {
        Utilisateur u = repo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
        if (!Boolean.TRUE.equals(u.getPinDefini()) || u.getCodePin() == null)
            throw new BadRequestException("Aucun PIN défini — configurez un PIN dans votre profil");
        if (u.getPinBloqueJusquA() != null && LocalDateTime.now().isBefore(u.getPinBloqueJusquA()))
            throw new BadRequestException("PIN temporairement bloqué — réessayez dans quelques minutes");
        // 400 et non 401 : sur cet endpoint, 401 doit signifier uniquement "JWT expiré"
        // pour que l'app puisse distinguer PIN erroné et session expirée
        if (!encoder.matches(pin, u.getCodePin()))
            throw new BadRequestException("PIN incorrect");
        return ApiResponse.success(null, "PIN valide");
    }

    // ── Lister les sessions actives ───────────────────────────────────────────
    @Override
    public List<SessionResponse> listerSessions(Long userId, String currentDeviceId) {
        return sessionRepository.findByUtilisateurIdAndActiveTrue(userId).stream()
                .map(s -> SessionResponse.builder()
                        .id(s.getId())
                        .deviceName(s.getDeviceName() != null ? s.getDeviceName() : "Appareil inconnu")
                        .deviceId(s.getDeviceId())
                        .createdAt(s.getCreatedAt())
                        .lastUsedAt(s.getLastUsedAt())
                        .active(Boolean.TRUE.equals(s.getActive()))
                        .current(s.getDeviceId().equals(currentDeviceId))
                        .build())
                .collect(Collectors.toList());
    }

    // ── Révoquer une session par ID ────────────────────────────────────────────
    @Override
    public ApiResponse<String> revoquerSession(Long sessionId, Long userId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session non trouvée"));
        if (!session.getUtilisateur().getId().equals(userId))
            throw new ForbiddenException("Vous ne pouvez révoquer que vos propres sessions");
        session.setActive(false);
        sessionRepository.save(session);
        auditService.log(userId, null, "REVOQUER_SESSION", true, "sessionId=" + sessionId);
        return ApiResponse.success(null, "Session révoquée avec succès");
    }

    // ── Révoquer toutes les sessions sauf la courante ─────────────────────────
    @Override
    public ApiResponse<String> revoquerToutesLesSessions(Long userId, Long exceptSessionId) {
        if (exceptSessionId != null) {
            sessionRepository.deactivateAllExcept(userId, exceptSessionId);
        } else {
            sessionRepository.deactivateAll(userId);
        }
        auditService.log(userId, null, "REVOQUER_TOUTES_SESSIONS", true,
                "exceptSessionId=" + exceptSessionId);
        return ApiResponse.success(null, "Toutes les autres sessions ont été déconnectées");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private ConnexionPinResponse toConnexionPinResponse(AuthResponse auth) {
        return ConnexionPinResponse.builder()
                .action("CONNECTE")
                .accessToken(auth.getAccessToken())
                .refreshToken(auth.getRefreshToken())
                .tokenType(auth.getTokenType())
                .expiresIn(auth.getExpiresIn())
                .pinDefini(auth.getPinDefini())
                .utilisateur(auth.getUtilisateur())
                .build();
    }

}