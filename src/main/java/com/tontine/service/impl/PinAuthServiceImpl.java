package com.tontine.service.impl;

import com.tontine.dto.request.*;
import com.tontine.dto.response.*;
import com.tontine.entity.Utilisateur;
import com.tontine.exception.*;
import com.tontine.repository.UtilisateurRepository;
import com.tontine.security.JwtService;
import com.tontine.service.*;
import com.tontine.service.AuditService;
import com.tontine.service.EmailAsyncService;
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

@Service @RequiredArgsConstructor @Slf4j @Transactional
public class PinAuthServiceImpl implements PinAuthService {

    private final UtilisateurRepository repo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final NotificationService notifService;
    private final SmsAsyncService smsAsyncService;
    private final EmailAsyncService emailAsyncService;
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
    public ApiResponse<AuthResponse> connecterAvecPin(ConnexionPinRequest request) {
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
            // Incrément atomique pour éviter la race condition sur deux requêtes simultanées
            repo.incrementTentativesEchouees(u.getId());
            u = repo.findById(u.getId()).orElseThrow();
            int tentatives = u.getTentativesPinEchouees() == null ? 1 : u.getTentativesPinEchouees();
            int restantes = maxTentatives - tentatives;

            if (tentatives >= maxTentatives) {
                u.setPinBloqueJusquA(LocalDateTime.now().plusMinutes(blocageMinutes));
                repo.save(u);
                auditService.log(u.getId(), u.getTelephone(), "CONNEXION_PIN", false,
                        "Compte bloqué après " + tentatives + " tentatives");
                smsAsyncService.envoyerSmsAsync(u.getTelephone(),
                        "AdasheCash - Sécurité : votre compte a été bloqué " + blocageMinutes
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

        // Succès
        u.setTentativesPinEchouees(0);
        u.setPinBloqueJusquA(null);
        repo.save(u);

        log.info("Connexion PIN réussie: {}", u.getTelephone());
        auditService.log(u.getId(), u.getTelephone(), "CONNEXION_PIN", true, null);
        return ApiResponse.success(authHelper.genererAuthResponse(u), "Connexion réussie");
    }

    // ── 3. Demander reset PIN (envoie OTP par SMS ou email) ───────────────────
    @Override
    public ApiResponse<String> demanderResetPin(ResetPinRequest request) {
        Utilisateur u = repo.findByTelephone(request.getTelephone())
                .orElseThrow(() -> new ResourceNotFoundException("Aucun compte trouvé avec ce numéro"));

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

        String canal = request.getCanal() != null ? request.getCanal().toUpperCase() : "SMS";

        if ("EMAIL".equals(canal)) {
            if (u.getEmail() == null || u.getEmail().isBlank())
                throw new BadRequestException("Aucun email associé à ce compte");
            notifService.envoyerEmail(u.getEmail(),
                    "Réinitialisation de votre PIN Tontine+",
                    "Votre code de réinitialisation : " + otp + "\nValable " + otpExpiration + " minutes.");
            return ApiResponse.success(null,
                    "Code envoyé à " + masquerEmail(u.getEmail()));
        } else {
            smsAsyncService.envoyerSmsAsync(u.getTelephone(),
                    "AdasheCash - Code reset PIN : " + otp + ". Valable " + otpExpiration + " min.");
            return ApiResponse.success(null,
                    "Code envoyé au " + masquerTelephone(u.getTelephone()));
        }
    }

    // ── 4. Confirmer reset PIN avec OTP + nouveau PIN ─────────────────────────
    @Override
    public ApiResponse<AuthResponse> reinitialiserPin(NouveauPinRequest request) {
        if (!request.getNouveauPin().equals(request.getConfirmPin()))
            throw new BadRequestException("Les deux PINs ne correspondent pas");
        if (!request.getNouveauPin().matches("\\d{4}"))
            throw new BadRequestException("Le PIN doit contenir exactement 4 chiffres");

        Utilisateur u = repo.findByTelephone(request.getTelephone())
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
                    "Sécurité AdasheCash — votre PIN a été réinitialisé",
                    "Bonjour " + u.getPrenom() + ",\n\n"
                    + "Votre PIN AdasheCash a été réinitialisé avec succès.\n"
                    + "Si vous n'êtes pas à l'origine de cette action, "
                    + "contactez-nous immédiatement.\n\n"
                    + "L'équipe AdasheCash");
        }
        return ApiResponse.success(authHelper.genererAuthResponse(u),
                "PIN réinitialisé avec succès ! Vous êtes maintenant connecté.");
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
                    "Sécurité AdasheCash — votre PIN a été modifié",
                    "Bonjour " + u.getPrenom() + ",\n\n"
                    + "Votre PIN AdasheCash a été modifié avec succès.\n"
                    + "Si vous n'êtes pas à l'origine de cette modification, "
                    + "réinitialisez immédiatement votre PIN via l'application.\n\n"
                    + "L'équipe AdasheCash");
        }
        return ApiResponse.success(null, "PIN modifié avec succès. Veuillez vous reconnecter.");
    }

}