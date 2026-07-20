package com.tontine.service.impl;

import com.tontine.dto.request.*;
import com.tontine.dto.response.*;
import com.tontine.entity.*;
import com.tontine.enums.*;
import com.tontine.exception.*;
import com.tontine.repository.*;
import com.tontine.service.EmailAsyncService;
import com.tontine.service.NotificationService;
import com.tontine.service.TontineService;
import com.tontine.websocket.TirageWebSocketHandler;
import com.tontine.websocket.TirageWsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TontineServiceImpl implements TontineService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Délai laissé au gagnant pour répondre avant qu'on considère son silence comme une acceptation. */
    public static final int FENETRE_REPONSE_MINUTES = 15;

    private final TontineRepository tontineRepository;
    private final MembreTontineRepository membreRepository;
    private final CotisationRepository cotisationRepository;
    private final TirageRepository tirageRepository;
    private final TirageInteretRepository tirageInteretRepository;
    private final TirageLitigeRepository tirageLitigeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CompteWalletRepository compteWalletRepository;
    private final NotificationService notificationService;
    private final EmailAsyncService emailAsyncService;
    private final com.tontine.util.SecurityUtil securityUtil;
    private final TirageWebSocketHandler tirageWsHandler;
    private final VirementCommissionService virementCommissionService;
    private final VirementCagnotteService   virementCagnotteService;

    // ── Création ─────────────────────────────────────────────────────────────

    @Override
    public TontineResponse creerTontine(TontineRequest request, Long createurId) {
        Utilisateur createur = utilisateurRepository.findById(createurId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // Garde-fou défense-en-profondeur : le client mobile vérifie déjà ce flag
        // (UtilisateurResponse.contratAdminAccepte) avant même d'afficher le
        // formulaire de création, mais on le revérifie ici au cas où.
        if (!com.tontine.util.ContratAdminVersion.estAcceptee(createur.getContratAdminVersion())) {
            throw new ForbiddenException("Vous devez accepter les conditions administrateur avant de créer une tontine");
        }

        Tontine tontine = Tontine.builder()
                .nom(request.getNom())
                .description(request.getDescription())
                .montantContribution(request.getMontantContribution())
                .devise(request.getDevise() != null ? request.getDevise() : "XAF")
                .frequence(request.getFrequence())
                .typeTirage(request.getTypeTirage())
                .dateDebut(request.getDateDebut())
                .dateProchainCycle(request.getDateDebut())
                .nombreMaxMembres(request.getNombreMaxMembres())
                .codeInvitation(genererCodeInvitation())
                .createur(createur)
                .build();

        tontine = tontineRepository.save(tontine);

        boolean estCotisant = request.getCotisant() == null || request.getCotisant();
        MembreTontine membreCreateur = MembreTontine.builder()
                .utilisateur(createur)
                .tontine(tontine)
                .roleMembreTontine(MembreTontineRole.CREATEUR)
                .ordreTour(estCotisant ? 1 : null)
                .statutMembre(MembreStatut.ACTIF)
                .actif(true)
                .build();
        membreRepository.save(membreCreateur);

        log.info("Tontine créée: {} par userId={}", tontine.getId(), createurId);
        return toResponse(tontine, createurId);
    }

    // ── Récupération — FILTRÉE PAR MEMBRE ────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<TontineResponse> getMesTontines(Long userId, Pageable pageable) {
        List<Tontine> tontines = tontineRepository.findAllByMembreId(userId, pageable).getContent();
        if (tontines.isEmpty()) return Collections.emptyList();

        List<Long> tontineIds = tontines.stream().map(Tontine::getId).toList();

        Map<Long, BigDecimal> totauxParTontine = cotisationRepository
                .sumMontantPayeGroupByTontineIds(tontineIds).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (BigDecimal) r[1]));

        Map<Long, BigDecimal> distribuesParTontine = tirageRepository
                .sumMontantDistribueGroupByTontineIds(tontineIds).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (BigDecimal) r[1]));

        Map<Long, List<MembreTontine>> membresParTontine = membreRepository
                .findByTontineIdInAndStatutMembreNot(tontineIds, MembreStatut.RETIRE).stream()
                .collect(Collectors.groupingBy(m -> m.getTontine().getId()));

        List<Long> tousMembreIds = membresParTontine.values().stream()
                .flatMap(List::stream).map(MembreTontine::getId).distinct().toList();
        Map<Long, BigDecimal> totauxParMembre = tousMembreIds.isEmpty()
                ? Collections.emptyMap()
                : cotisationRepository.sumMontantPayeGroupByMembreIds(tousMembreIds).stream()
                        .collect(Collectors.toMap(r -> (Long) r[0], r -> (BigDecimal) r[1]));

        // 1 query pour tous les cycles actuels (remplace la boucle N×findMembreIdsAyantPayePourCycle)
        Map<Long, Set<Long>> payesParTontine = cotisationRepository
                .findMembreIdsAyantPayePourCyclesActuels(tontineIds).stream()
                .collect(Collectors.groupingBy(
                        r -> (Long) r[0],
                        Collectors.mapping(r -> (Long) r[1], Collectors.toSet())));
        tontines.forEach(t -> payesParTontine.putIfAbsent(t.getId(), Collections.emptySet()));

        // Réutilise le userId du paramètre (déjà authentifié par le contrôleur)
        // pour éviter un second appel SecurityContext et garantir la cohérence.
        return tontines.stream()
                .map(t -> toResponseBatch(t, totauxParTontine, distribuesParTontine, membresParTontine, totauxParMembre, payesParTontine, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TontineResponse getTontineById(Long tontineId, Long userId) {
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));
        // Vérifier appartenance stricte
        verifierAccesTontine(tontineId, userId);
        return toResponse(tontine, userId);
    }

    // ── Membres ───────────────────────────────────────────────────────────────

    @Override
    public MembreResponse ajouterMembre(Long tontineId, AjoutMembreRequest request, Long adminId) {
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));

        verifierEstAdmin(tontineId, adminId);

        if (tontine.getNombreMembresActifs() >= tontine.getNombreMaxMembres()) {
            throw new BadRequestException("La tontine a atteint le nombre maximum de membres");
        }

        Utilisateur utilisateur;
        if (request.getUtilisateurId() != null) {
            utilisateur = utilisateurRepository.findById(request.getUtilisateurId())
                    .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
        } else if (request.getTelephone() != null) {
            utilisateur = utilisateurRepository.findByTelephone(request.getTelephone())
                    .orElseThrow(() -> new ResourceNotFoundException("Aucun compte avec ce numéro: " + request.getTelephone()));
        } else {
            throw new BadRequestException("Fournir un userId ou un numéro de téléphone");
        }

        Optional<MembreTontine> existant = membreRepository.findByUtilisateurIdAndTontineId(utilisateur.getId(), tontineId);
        if (existant.isPresent()) {
            MembreTontine ex = existant.get();
            if (ex.getStatutMembre() != MembreStatut.RETIRE) {
                throw new BadRequestException("Cet utilisateur est déjà membre de cette tontine");
            }
            // Ré-inviter un membre retiré : remettre EN_ATTENTE + reset du timer d'expiration
            ex.setStatutMembre(MembreStatut.EN_ATTENTE);
            ex.setActif(false);
            ex.setDateAdhesion(LocalDateTime.now()); // repart à 0 pour les 24 h d'expiration
            MembreTontine saved = membreRepository.save(ex);
            notificationService.creerNotification(utilisateur, tontine,
                    "Nouvelle invitation — " + tontine.getNom(),
                    "Vous avez été de nouveau invité. Ouvrez l'app pour accepter.",
                    NotificationType.INVITATION);
            return toMembreResponse(saved, tontine);
        }

        int prochain = membreRepository.findByTontineId(tontineId).size() + 1;
        MembreTontine membre = MembreTontine.builder()
                .utilisateur(utilisateur)
                .tontine(tontine)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .ordreTour(request.getOrdreTour() != null ? request.getOrdreTour() : prochain)
                .statutMembre(MembreStatut.EN_ATTENTE)
                .actif(false)
                .build();
        membre = membreRepository.save(membre);

        notificationService.creerNotification(utilisateur, tontine,
                "Invitation à rejoindre " + tontine.getNom(),
                "Vous avez été invité. Ouvrez l'app pour accepter et rejoindre la tontine.",
                NotificationType.NOUVEAU_MEMBRE);

        return toMembreResponse(membre, tontine);
    }

    @Override
    public ApiResponse<String> rejoindreParCode(String code, Long userId) {
        // Les codes sont générés en majuscules — normalise la saisie (minuscules, espaces)
        String codeNormalise = code == null ? "" : code.trim().toUpperCase();
        Tontine tontine = tontineRepository.findByCodeInvitation(codeNormalise)
                .orElseThrow(() -> new ResourceNotFoundException("Code d'invitation invalide"));

        if (tontine.getStatut() == com.tontine.enums.TontineStatus.TERMINEE) {
            throw new BadRequestException("Impossible de rejoindre une tontine terminée");
        }

        if (membreRepository.existsByUtilisateurIdAndTontineId(userId, tontine.getId())) {
            throw new BadRequestException("Vous êtes déjà membre de cette tontine");
        }

        verifierWalletMobileMoney(userId);

        AjoutMembreRequest req = new AjoutMembreRequest();
        req.setUtilisateurId(userId);
        ajouterMembre(tontine.getId(), req, tontine.getCreateur().getId());
        // L'utilisateur rejoint par code = acceptation immédiate
        accepterInvitation(tontine.getId(), userId);
        return ApiResponse.success(null, "Vous avez rejoint la tontine: " + tontine.getNom());
    }

    @Override
    @Transactional
    public ApiResponse<String> accepterInvitation(Long tontineId, Long userId) {
        MembreTontine membre = membreRepository.findByUtilisateurIdAndTontineId(userId, tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation non trouvée"));

        if (membre.getStatutMembre() == MembreStatut.ACTIF) {
            throw new BadRequestException("Vous êtes déjà membre actif de cette tontine");
        }
        if (membre.getStatutMembre() == MembreStatut.RETIRE) {
            throw new BadRequestException("Votre invitation a été annulée");
        }

        verifierWalletMobileMoney(userId);

        membre.setStatutMembre(MembreStatut.ACTIF);
        membre.setActif(true);
        membreRepository.save(membre);

        notificationService.creerNotification(membre.getUtilisateur(), membre.getTontine(),
                "Bienvenue dans " + membre.getTontine().getNom() + " !",
                "Vous avez accepté l'invitation. Cotisation: "
                        + membre.getTontine().getMontantContribution() + " " + membre.getTontine().getDevise(),
                NotificationType.NOUVEAU_MEMBRE);

        String emailMembre = membre.getUtilisateur().getEmail();
        if (emailMembre != null && !emailMembre.isBlank()) {
            Tontine t = membre.getTontine();
            emailAsyncService.envoyerEmailAsync(emailMembre,
                    "Bienvenue dans la tontine " + t.getNom(),
                    "Bonjour " + membre.getUtilisateur().getPrenom() + ",\n\n"
                    + "Vous avez rejoint la tontine \"" + t.getNom() + "\".\n\n"
                    + "Montant de cotisation : " + t.getMontantContribution() + " " + t.getDevise()
                    + "\nFréquence : " + t.getFrequence().name().toLowerCase() + "\n\n"
                    + "Bonne tontine !\nL'équipe Adashe");
        }

        return ApiResponse.success(null, "Invitation acceptée — vous êtes maintenant membre de " + membre.getTontine().getNom());
    }

    @Override
    @Transactional
    public ApiResponse<String> declinerInvitation(Long tontineId, Long userId) {
        MembreTontine membre = membreRepository.findByUtilisateurIdAndTontineId(userId, tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation non trouvée"));

        if (membre.getStatutMembre() != MembreStatut.EN_ATTENTE) {
            throw new BadRequestException("Aucune invitation en attente pour cette tontine");
        }

        membre.setStatutMembre(MembreStatut.RETIRE);
        membre.setActif(false);
        membreRepository.save(membre);

        return ApiResponse.success(null, "Invitation déclinée");
    }

    @Override
    public ApiResponse<String> retirerMembre(Long tontineId, Long membreId, Long adminId) {
        verifierEstAdmin(tontineId, adminId);
        MembreTontine membre = membreRepository.findById(membreId)
                .orElseThrow(() -> new ResourceNotFoundException("Membre non trouvé"));

        if (membre.getRoleMembreTontine() == MembreTontineRole.CREATEUR) {
            throw new BadRequestException("Impossible de retirer le créateur");
        }
        membre.setActif(false);
        membre.setStatutMembre(MembreStatut.RETIRE);
        membreRepository.save(membre);

        Tontine tontine = membre.getTontine();
        notificationService.creerNotification(membre.getUtilisateur(), tontine,
                "Retrait de la tontine",
                "Vous avez été retiré de la tontine « " + tontine.getNom()
                        + " » par l'administrateur. Contactez-le pour toute question.",
                NotificationType.MEMBRE_RETIRE);

        return ApiResponse.success(null, "Membre retiré");
    }

    @Override
    public ApiResponse<String> supprimerTontine(Long tontineId, Long adminId) {
        verifierEstAdmin(tontineId, adminId);
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));

        boolean estTerminee = tontine.getStatut() == com.tontine.enums.TontineStatus.TERMINEE;
        if (!estTerminee) {
            BigDecimal totalCollecte = cotisationRepository.sumMontantPayeByTontineId(tontineId);
            boolean aucuneCotisation = totalCollecte == null || totalCollecte.compareTo(BigDecimal.ZERO) == 0;
            if (!aucuneCotisation) {
                throw new BadRequestException(
                    "Impossible de supprimer : des membres ont déjà cotisé. " +
                    "Attendez que la tontine soit terminée.");
            }
        }

        tontine.setDeletedAt(LocalDateTime.now());
        tontineRepository.save(tontine);
        log.info("Tontine {} soft-deleted par userId={}", tontineId, adminId);
        return ApiResponse.success(null, "Tontine supprimée");
    }

    @Override
    public TontineResponse modifierTontine(Long tontineId, com.tontine.dto.request.ModifierTontineRequest request, Long adminId) {
        verifierEstAdmin(tontineId, adminId);
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));

        if (tontine.getStatut() == com.tontine.enums.TontineStatus.TERMINEE) {
            throw new BadRequestException("Impossible de modifier une tontine terminée");
        }

        // Champs toujours modifiables
        tontine.setNom(request.getNom().trim());
        if (request.getDescription() != null) {
            tontine.setDescription(request.getDescription().trim());
        }
        if (request.getDateProchainCycle() != null) {
            tontine.setDateProchainCycle(request.getDateProchainCycle());
        }
        if (request.getTirageHeure() != null && !request.getTirageHeure().isBlank()) {
            tontine.setTirageHeure(request.getTirageHeure().trim());
        }

        // montantContribution, frequence, typeTirage : verrouillés après démarrage
        // → intentionnellement non modifiables ici, le client Android ne les envoie pas

        tontineRepository.save(tontine);
        log.info("Tontine {} modifiée par userId={}", tontineId, adminId);
        return toResponse(tontine, adminId);
    }

    @Override
    @Transactional
    public TontineResponse configurerTontine(Long tontineId, ConfigurerTontineRequest request, Long createurId) {
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));
        verifierEstCreateur(tontine, createurId);

        if (request.getNumeroMtnMomo() == null && request.getNumeroOrangeMomo() == null) {
            throw new BadRequestException("Renseignez au moins un numéro Mobile Money (MTN ou Orange)");
        }

        // La commission est verrouillée dès le démarrage — configurable uniquement en EN_ATTENTE
        Float nouvelleCommission = request.getCommissionPourcent() != null
                ? request.getCommissionPourcent() : 0.0f;
        if (tontine.getStatut() != com.tontine.enums.TontineStatus.EN_ATTENTE) {
            if (!nouvelleCommission.equals(tontine.getCommissionPourcent())) {
                throw new BadRequestException(
                        "La commission ne peut plus être modifiée après le démarrage de la tontine");
            }
        } else {
            tontine.setCommissionPourcent(nouvelleCommission);
        }
        tontine.setNumeroMtnMomo(request.getNumeroMtnMomo());
        tontine.setNumeroOrangeMomo(request.getNumeroOrangeMomo());

        tontineRepository.save(tontine);

        // Sync vers le portefeuille du créateur (évite la double saisie côté profil)
        synchroniserNumeroVersWallet(createurId,
                com.tontine.enums.PaiementMode.MTN_MOBILE_MONEY, request.getNumeroMtnMomo());
        synchroniserNumeroVersWallet(createurId,
                com.tontine.enums.PaiementMode.ORANGE_MONEY, request.getNumeroOrangeMomo());

        log.info("Tontine {} configurée (commission={}%) par userId={}", tontineId, request.getCommissionPourcent(), createurId);
        return toResponse(tontine, createurId);
    }

    // Remplit le compte wallet de l'opérateur uniquement s'il n'a pas encore de numéro —
    // on n'écrase jamais un numéro personnel défini dans le profil
    private void synchroniserNumeroVersWallet(Long userId,
                                              com.tontine.enums.PaiementMode operateur,
                                              String numero) {
        if (numero == null || numero.isBlank()) return;
        CompteWallet compte = compteWalletRepository
                .findByUtilisateurIdAndOperateur(userId, operateur)
                .orElse(null);
        if (compte == null) {
            Utilisateur utilisateur = utilisateurRepository.findById(userId).orElse(null);
            if (utilisateur == null) return;
            compte = CompteWallet.builder()
                    .utilisateur(utilisateur)
                    .operateur(operateur)
                    .build();
        } else if (compte.getTelephone() != null && !compte.getTelephone().isBlank()) {
            return;
        }
        compte.setTelephone(numero);
        compte.setActif(true);
        compteWalletRepository.save(compte);
    }

    @Override
    public TontineResponse demarrerTontine(Long tontineId, Long adminId) {
        verifierEstAdmin(tontineId, adminId);
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));

        if (tontine.getStatut() != com.tontine.enums.TontineStatus.EN_ATTENTE) {
            throw new BadRequestException("La tontine est déjà démarrée ou terminée");
        }
        if (tontine.getNombreMembresActifs() < 2) {
            throw new BadRequestException("Ajoutez au moins 2 membres avant de démarrer");
        }
        if ((tontine.getNumeroMtnMomo() == null || tontine.getNumeroMtnMomo().isBlank())
                && (tontine.getNumeroOrangeMomo() == null || tontine.getNumeroOrangeMomo().isBlank())) {
            throw new BadRequestException("Configurez au moins un numéro de paiement Mobile Money avant de démarrer");
        }
        // L'admin doit avoir au moins un compte wallet actif pour recevoir sa commission
        if (compteWalletRepository.findActifsByUtilisateurId(adminId).isEmpty()) {
            throw new BadRequestException(
                "Ajoutez et activez au moins un compte Mobile Money (MTN ou Orange) dans votre profil avant de démarrer la tontine");
        }

        tontine.setStatut(com.tontine.enums.TontineStatus.ACTIVE);
        int cycleDays = switch (tontine.getFrequence()) {
            case HEBDOMADAIRE -> 7;
            case BIMENSUEL    -> 14;
            case MENSUEL      -> 30;
        };
        tontine.setDateProchainCycle(java.time.LocalDate.now().plusDays(cycleDays));
        tontineRepository.save(tontine);

        // Notifier tous les membres actifs
        membreRepository.findByTontineIdAndActifTrue(tontine.getId()).forEach(m ->
            notificationService.creerNotification(m.getUtilisateur(), tontine,
                "La tontine « " + tontine.getNom() + " » a démarré !",
                "Le 1er cycle est ouvert. Effectuez votre cotisation.",
                com.tontine.enums.NotificationType.NOUVEAU_CYCLE)
        );

        return toResponse(tontine, adminId);
    }

    @Override
    public TontineResponse terminerTontine(Long tontineId, Long createurId) {
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));
        verifierEstCreateur(tontine, createurId);

        if (tontine.getStatut() != com.tontine.enums.TontineStatus.ACTIVE) {
            throw new BadRequestException("Seule une tontine ACTIVE peut être terminée manuellement");
        }

        tontine.setStatut(com.tontine.enums.TontineStatus.TERMINEE);
        tontineRepository.save(tontine);

        membreRepository.findByTontineIdAndActifTrue(tontineId).forEach(m ->
            notificationService.creerNotification(m.getUtilisateur(), tontine,
                "Tontine clôturée",
                "La tontine « " + tontine.getNom() + " » a été clôturée par le créateur.",
                com.tontine.enums.NotificationType.NOUVEAU_CYCLE)
        );

        log.info("Tontine {} terminée manuellement par userId={}", tontineId, createurId);
        return toResponse(tontine, createurId);
    }

    // ── Cotisations — CRÉATEUR UNIQUEMENT ─────────────────────────────────────

    @Override
    @CacheEvict(value = "statistiques", key = "#request.tontineId")
    public CotisationResponse enregistrerCotisation(CotisationRequest request, Long userId) {
        Tontine tontine = tontineRepository.findById(request.getTontineId())
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));

        MembreTontine membre = membreRepository.findById(request.getMembreId())
                .orElseThrow(() -> new ResourceNotFoundException("Membre non trouvé"));

        boolean estCreateur = tontine.getCreateur().getId().equals(userId);
        boolean cotisePourSoiMeme = membre.getUtilisateur().getId().equals(userId);

        // Un membre peut cotiser uniquement pour lui-même ; le créateur peut cotiser pour n'importe qui
        if (!estCreateur && !cotisePourSoiMeme) {
            throw new ForbiddenException("Vous ne pouvez cotiser que pour vous-même.");
        }

        // Vérifier que l'utilisateur est bien membre de la tontine
        if (!estCreateur) {
            membreRepository.findByUtilisateurIdAndTontineId(userId, tontine.getId())
                    .orElseThrow(() -> new ForbiddenException("Vous n'êtes pas membre de cette tontine"));
        }

        // Cycle cible : passé (rattrapage) ou actuel
        int cycleCible = (request.getNumeroCycle() != null && request.getNumeroCycle() < tontine.getCycleActuel())
                ? request.getNumeroCycle()
                : tontine.getCycleActuel();

        boolean estRattrapage = cycleCible < tontine.getCycleActuel();

        // Rattrapage Mobile Money autorisé uniquement si un tirage est en attente de validation.
        // Dès que le paiement du bénéficiaire est validé (confirme = true), seul l'espèces est accepté.
        if (estRattrapage) {
            String mode = request.getModePaiement();
            if (mode != null && !mode.equalsIgnoreCase("ESPECES")) {
                boolean tirageEnAttente = tirageRepository.existsByTontineIdAndConfirmeFalse(tontine.getId());
                if (!tirageEnAttente) {
                    throw new BadRequestException(
                            "Le rattrapage d'un cycle passé ne peut être enregistré qu'en espèces.");
                }
            }
        }

        // Calculer l'amende pour rattrapage
        BigDecimal montantAmende = estRattrapage ? tontine.getMontantAmende() : BigDecimal.ZERO;
        BigDecimal montantAttendu = tontine.getMontantContribution().add(montantAmende);

        if (request.getMontant().compareTo(montantAttendu) < 0) {
            throw new BadRequestException(estRattrapage
                    ? "Montant insuffisant pour le rattrapage : " + montantAttendu + " " + tontine.getDevise()
                      + " requis (cotisation " + tontine.getMontantContribution()
                      + " + amende " + montantAmende + ")"
                    : "Montant insuffisant : " + request.getMontant() + " < " + tontine.getMontantContribution()
                      + " " + tontine.getDevise());
        }

        boolean dejaPaye = cotisationRepository
                .findByMembreIdAndTontineIdAndNumeroCycle(membre.getId(), tontine.getId(), cycleCible)
                .filter(c -> c.getStatut() == PaiementStatus.PAYE).isPresent();

        if (dejaPaye) throw new BadRequestException("Ce membre a déjà cotisé pour ce cycle");

        // Vérifier l'ordre des cycles en 2 requêtes (évite le N+1)
        // Pour un rattrapage : tous les cycles trackés avant cycleCible doivent être payés
        int limiteVerif = estRattrapage ? cycleCible : tontine.getCycleActuel();
        if (limiteVerif > 1) {
            Set<Integer> cyclesTrackes = cotisationRepository
                    .findCyclesTrackesAvant(tontine.getId(), limiteVerif);
            if (!cyclesTrackes.isEmpty()) {
                Set<Integer> cyclesPayesMembre = cotisationRepository
                        .findCyclesPayesParMembre(membre.getId(), tontine.getId(), limiteVerif);
                for (int cycle = 1; cycle < limiteVerif; cycle++) {
                    if (cyclesTrackes.contains(cycle) && !cyclesPayesMembre.contains(cycle)) {
                        throw new BadRequestException(
                                "Le cycle " + cycle + " doit être réglé avant de payer le cycle " + cycleCible);
                    }
                }
            }
        }

        Cotisation cotisation = Cotisation.builder()
                .tontine(tontine)
                .membre(membre)
                .montant(montantAttendu)
                .montantAmende(montantAmende)
                .numeroCycle(cycleCible)
                .statut(PaiementStatus.PAYE)
                .estEnRetard(estRattrapage)
                .datePaiement(LocalDate.now())
                .referenceTransaction(request.getReferenceTransaction())
                .modePaiement(request.getModePaiement() != null ? request.getModePaiement() : "ESPECES")
                .commentaire(request.getCommentaire())
                .build();

        cotisation = cotisationRepository.save(cotisation);

        notificationService.creerNotification(membre.getUtilisateur(), tontine,
                "Paiement confirmé ✓",
                "Cotisation de " + request.getMontant() + " " + tontine.getDevise() + " enregistrée.",
                NotificationType.PAIEMENT_RECU);

        return toCotisationResponse(cotisation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CotisationResponse> getCotisationsTontine(Long tontineId, Long userId, Pageable pageable) {
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));
        membreRepository.findByUtilisateurIdAndTontineId(userId, tontine.getId())
                .orElseThrow(() -> new ForbiddenException("Vous n'êtes pas membre de cette tontine"));
        return cotisationRepository.findByTontineIdOrderByCreatedAtDesc(tontineId, pageable)
                .getContent().stream().map(this::toCotisationResponse).collect(Collectors.toList());
    }

    // ── Tirage — CRÉATEUR UNIQUEMENT ─────────────────────────────────────────

    @Override
    @CacheEvict(value = "statistiques", key = "#tontineId")
    public TirageResponse effectuerTirage(Long tontineId, TirageRequest request, Long userId) {
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));

        verifierEstAdmin(tontineId, userId);

        if (tontine.getStatut() == com.tontine.enums.TontineStatus.TERMINEE) {
            throw new BadRequestException("Cette tontine est terminée, aucun tirage possible");
        }

        if (tirageRepository.existsByTontineIdAndNumeroCycle(tontineId, tontine.getCycleActuel())) {
            throw new BadRequestException("Un tirage a déjà été effectué pour ce cycle");
        }

        // Vérifier que tous les membres non-bloqués ont cotisé pour ce cycle
        List<MembreTontine> membresActifs = membreRepository.findByTontineIdAndActifTrue(tontineId)
                .stream()
                .filter(m -> m.getStatutMembre() != MembreStatut.BLOQUE)
                .collect(Collectors.toList());
        Set<Long> ayantPaye = cotisationRepository.findMembreIdsAyantPayePourCycle(tontineId, tontine.getCycleActuel());
        long nonPayes = membresActifs.stream()
                .filter(m -> !ayantPaye.contains(m.getId()))
                .count();
        if (nonPayes > 0) {
            throw new BadRequestException(nonPayes + " membre(s) n'ont pas encore cotisé pour ce cycle");
        }

        Utilisateur admin = utilisateurRepository.findById(userId).orElseThrow();
        MembreTontine beneficiaire = choisirBeneficiaire(tontine, request);
        BigDecimal brut = tontine.getMontantContribution()
                .multiply(BigDecimal.valueOf(tontine.getNombreMembresActifs()));
        BigDecimal commission = brut
                .multiply(BigDecimal.valueOf(tontine.getCommissionPourcent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal cagnotte = brut.subtract(commission);

        // Le tirage est créé en attente de validation admin (confirme = false) ET
        // en attente de réponse du gagnant (statutAcceptation = EN_ATTENTE) : il a
        // FENETRE_REPONSE_MINUTES pour accepter ou décliner avant que le scheduler
        // ne considère son silence comme une acceptation implicite.
        Tirage tirage = Tirage.builder()
                .tontine(tontine)
                .beneficiaire(beneficiaire)
                .effectuePar(admin)
                .numeroCycle(tontine.getCycleActuel())
                .montantDistribue(cagnotte)
                .commissionPrelevee(commission)
                .methodeTirage(tontine.getTypeTirage())
                .dateTirage(LocalDate.now())
                .confirme(false)
                .dateExpirationReponse(LocalDateTime.now().plusMinutes(FENETRE_REPONSE_MINUTES))
                .commentaire(request.getCommentaire())
                .build();

        tirage = tirageRepository.save(tirage);
        beneficiaire.setACagnotteSurCycleActuel(true);
        membreRepository.save(beneficiaire);

        // Notifications immédiates — c'est aussi le départ du délai de réponse du gagnant.
        notifierTirage(tontine, beneficiaire, cagnotte);

        TirageResponse response = toTirageResponse(tirage);
        // Notifier les membres en temps réel — la roue commence à tourner sur leurs écrans
        tirageWsHandler.broadcast(tontineId, new TirageWsEvent("TIRAGE_LANCE", response));
        return response;
    }

    @Override
    @CacheEvict(value = "statistiques", key = "#tontineId")
    public TirageResponse repondreTirage(Long tontineId, Long tirageId, Long userId, boolean accepte) {
        Tirage tirage = tirageRepository.findByIdAndTontineId(tirageId, tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tirage non trouvé"));

        if (!tirage.getBeneficiaire().getUtilisateur().getId().equals(userId)) {
            throw new ForbiddenException("Seul le bénéficiaire désigné peut répondre à ce tirage");
        }
        if (tirage.getStatutAcceptation() != TirageAcceptationStatut.EN_ATTENTE) {
            throw new BadRequestException("Ce tirage a déjà reçu une réponse");
        }
        if (Boolean.TRUE.equals(tirage.getConfirme())) {
            throw new BadRequestException("Ce tirage est déjà confirmé");
        }
        if (Boolean.TRUE.equals(tirage.getEnLitige())) {
            throw new BadRequestException("Ce tirage fait l'objet d'un signalement en cours d'examen");
        }

        Tontine tontine = tirage.getTontine();
        Utilisateur gagnant = tirage.getBeneficiaire().getUtilisateur();

        if (accepte) {
            tirage.setStatutAcceptation(TirageAcceptationStatut.ACCEPTE);
            tirageRepository.save(tirage);
            log.info("Tirage {} accepté explicitement par userId={}", tirageId, userId);
        } else {
            tirage.setStatutAcceptation(TirageAcceptationStatut.DECLINE);
            tirageRepository.save(tirage);

            MembreTontine beneficiaire = tirage.getBeneficiaire();
            beneficiaire.setACagnotteSurCycleActuel(false);
            membreRepository.save(beneficiaire);

            notificationService.creerNotification(tontine.getCreateur(), tontine,
                    "🙋 Cagnotte déclinée — " + tontine.getNom(),
                    gagnant.getPrenom() + " " + gagnant.getNom()
                            + " a décliné sa cagnotte pour ce cycle. Choisissez un remplaçant.",
                    NotificationType.TIRAGE_EFFECTUE);

            log.info("Tirage {} décliné par userId={}", tirageId, userId);
        }

        TirageResponse response = toTirageResponse(tirage);
        tirageWsHandler.broadcast(tontineId, new TirageWsEvent(
                accepte ? "TIRAGE_ACCEPTE" : "TIRAGE_DECLINE", response));
        return response;
    }

    @Override
    @CacheEvict(value = "statistiques", key = "#tontineId")
    public TirageResponse confirmerTirage(Long tontineId, Long tirageId, Long adminId) {
        verifierEstAdmin(tontineId, adminId);

        Tirage tirage = tirageRepository.findByIdAndTontineId(tirageId, tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tirage non trouvé"));

        if (Boolean.TRUE.equals(tirage.getConfirme())) {
            throw new BadRequestException("Ce tirage est déjà confirmé");
        }
        if (tirage.getStatutAcceptation() == TirageAcceptationStatut.EN_ATTENTE) {
            throw new BadRequestException("Le bénéficiaire n'a pas encore répondu (15 minutes de délai)");
        }
        if (tirage.getStatutAcceptation() == TirageAcceptationStatut.DECLINE) {
            throw new BadRequestException("Ce bénéficiaire a décliné sa cagnotte — choisissez un remplaçant avant de confirmer");
        }
        if (Boolean.TRUE.equals(tirage.getEnLitige())) {
            throw new BadRequestException("Ce tirage fait l'objet d'un signalement en cours d'examen");
        }

        tirage.setConfirme(true);
        tirage = tirageRepository.save(tirage);

        Tontine tontine = tirage.getTontine();
        MembreTontine beneficiaire = tirage.getBeneficiaire();
        BigDecimal cagnotte = tirage.getMontantDistribue();

        // Avancer le cycle ou clore la tontine
        List<MembreTontine> restants = membreRepository.findEligiblesPourTirage(tontineId);
        if (restants.isEmpty()) {
            tontine.setStatut(com.tontine.enums.TontineStatus.TERMINEE);
        } else {
            tontine.setCycleActuel(tontine.getCycleActuel() + 1);
            tontine.setDateProchainCycle(calculerProchainCycle(tontine));
        }
        tontineRepository.save(tontine);

        // Notification ciblée : le tirage a déjà été annoncé à tout le monde dans
        // effectuerTirage() — ici on informe seulement le bénéficiaire que c'est
        // désormais confirmé (le cycle a avancé).
        notificationService.creerNotification(beneficiaire.getUtilisateur(), tontine,
                "✅ Cagnotte confirmée — " + tontine.getNom(),
                "Votre cagnotte de " + cagnotte + " " + tontine.getDevise() + " a été confirmée par l'administrateur.",
                NotificationType.TIRAGE_BENEFICIAIRE);

        // Email au bénéficiaire si email renseigné
        String emailBenef = beneficiaire.getUtilisateur().getEmail();
        if (emailBenef != null && !emailBenef.isBlank()) {
            String sujet = "🎉 Vous recevez la cagnotte — " + tontine.getNom();
            String corps = "Bonjour " + beneficiaire.getUtilisateur().getPrenom() + ",\n\n"
                    + "Félicitations ! Vous avez été sélectionné(e) comme bénéficiaire du cycle "
                    + tirage.getNumeroCycle() + " de la tontine \"" + tontine.getNom() + "\".\n\n"
                    + "Montant de la cagnotte : " + cagnotte + " " + tontine.getDevise() + "\n"
                    + "Date du tirage : " + tirage.getDateTirage() + "\n\n"
                    + "L'équipe Adashe";
            emailAsyncService.envoyerEmailAsync(emailBenef, sujet, corps);
        }

        // Virer la cagnotte au bénéficiaire après commit
        Long cagnotteId = virementCagnotteService.creerVirementEnAttente(tirage);
        if (cagnotteId != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    virementCagnotteService.effectuerVirementAsyncParId(cagnotteId);
                }
            });
        }

        // Prélever la commission après commit (même pattern que les amendes)
        List<Long> commissionIds = virementCommissionService.creerVirementsEnAttente(tirage);
        if (!commissionIds.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    virementCommissionService.effectuerVirementsAsyncParIds(commissionIds);
                }
            });
        }

        TirageResponse confirmed = toTirageResponse(tirage);
        // Notifier les membres que le tirage est validé (cycle avancé)
        tirageWsHandler.broadcast(tontineId, new TirageWsEvent("TIRAGE_CONFIRME", confirmed));

        log.info("Tirage {} confirmé pour tontine {} par adminId={}", tirageId, tontineId, adminId);
        return confirmed;
    }

    // ── Renégociation après déclin (membres intéressés) ──────────────────────

    @Override
    public ApiResponse<String> exprimerInteret(Long tontineId, Long userId, boolean interesse) {
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));
        MembreTontine membre = membreRepository.findByUtilisateurIdAndTontineId(userId, tontineId)
                .orElseThrow(() -> new ForbiddenException("Vous n'êtes pas membre de cette tontine"));

        int cycle = tontine.getCycleActuel();
        if (interesse) {
            boolean dejaInscrit = tirageInteretRepository
                    .findByTontineIdAndNumeroCycleAndMembreId(tontineId, cycle, membre.getId())
                    .isPresent();
            if (!dejaInscrit) {
                tirageInteretRepository.save(TirageInteret.builder()
                        .tontine(tontine).numeroCycle(cycle).membre(membre).build());
            }
            return ApiResponse.success(null, "Intérêt enregistré pour ce cycle");
        }

        tirageInteretRepository.deleteByTontineIdAndNumeroCycleAndMembreId(tontineId, cycle, membre.getId());
        return ApiResponse.success(null, "Intérêt retiré");
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembreResponse> getInteresses(Long tontineId, Long userId) {
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));
        verifierAccesTontine(tontineId, userId);

        return tirageInteretRepository.findByTontineIdAndNumeroCycle(tontineId, tontine.getCycleActuel())
                .stream()
                .map(i -> toMembreResponse(i.getMembre(), tontine))
                .collect(Collectors.toList());
    }

    @Override
    @CacheEvict(value = "statistiques", key = "#tontineId")
    public TirageResponse choisirRemplacant(Long tontineId, Long tirageId, Long nouveauMembreId, Long adminId) {
        verifierEstAdmin(tontineId, adminId);

        Tirage tirage = tirageRepository.findByIdAndTontineId(tirageId, tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tirage non trouvé"));

        if (tirage.getStatutAcceptation() != TirageAcceptationStatut.DECLINE) {
            throw new BadRequestException("Seul un tirage décliné peut recevoir un remplaçant");
        }
        if (Boolean.TRUE.equals(tirage.getEnLitige())) {
            throw new BadRequestException("Ce tirage fait l'objet d'un signalement en cours d'examen");
        }

        MembreTontine nouveauMembre = membreRepository.findById(nouveauMembreId)
                .orElseThrow(() -> new ResourceNotFoundException("Membre non trouvé"));
        if (!nouveauMembre.getTontine().getId().equals(tontineId)) {
            throw new BadRequestException("Ce membre n'appartient pas à cette tontine");
        }
        boolean eligible = membreRepository.findEligiblesPourTirage(tontineId)
                .stream().anyMatch(m -> m.getId().equals(nouveauMembreId));
        if (!eligible) {
            throw new BadRequestException("Ce membre n'est pas éligible pour recevoir la cagnotte ce cycle");
        }

        Tontine tontine = tirage.getTontine();

        // Même logique que le tirage initial : le remplaçant repart sur une
        // fenêtre de 15 min pour accepter ou décliner à son tour.
        tirage.setBeneficiaire(nouveauMembre);
        tirage.setStatutAcceptation(TirageAcceptationStatut.EN_ATTENTE);
        tirage.setDateExpirationReponse(LocalDateTime.now().plusMinutes(FENETRE_REPONSE_MINUTES));
        tirage = tirageRepository.save(tirage);

        nouveauMembre.setACagnotteSurCycleActuel(true);
        membreRepository.save(nouveauMembre);

        notifierTirage(tontine, nouveauMembre, tirage.getMontantDistribue());

        TirageResponse response = toTirageResponse(tirage);
        tirageWsHandler.broadcast(tontineId, new TirageWsEvent("TIRAGE_LANCE", response));

        log.info("Tirage {} : remplaçant choisi (membreId={}) par adminId={}", tirageId, nouveauMembreId, adminId);
        return response;
    }

    // ── Signalement/contestation (n'importe quel membre) ──────────────────────

    @Override
    public TirageLitigeResponse signalerLitige(Long tontineId, Long tirageId, Long userId, String motif) {
        Tirage tirage = tirageRepository.findByIdAndTontineId(tirageId, tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tirage non trouvé"));
        verifierAccesTontine(tontineId, userId);

        if (Boolean.TRUE.equals(tirage.getEnLitige())) {
            throw new BadRequestException("Un signalement est déjà en cours d'examen pour ce tirage");
        }

        Utilisateur signaleur = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        TirageLitige litige = tirageLitigeRepository.save(TirageLitige.builder()
                .tirage(tirage).signalePar(signaleur).motif(motif).build());

        tirage.setEnLitige(true);
        tirageRepository.save(tirage);

        Tontine tontine = tirage.getTontine();
        // Notification privée à l'admin uniquement — pas de diffusion publique
        // d'une accusation tant qu'elle n'a pas été examinée.
        notificationService.creerNotification(tontine.getCreateur(), tontine,
                "⚠️ Signalement sur le tirage — " + tontine.getNom(),
                signaleur.getPrenom() + " " + signaleur.getNom() + " a signalé un problème : " + motif,
                NotificationType.TIRAGE_SIGNALE);

        log.info("Tirage {} signalé par userId={}", tirageId, userId);
        return toTirageLitigeResponse(litige);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TirageLitigeResponse> getLitiges(Long tontineId, Long tirageId, Long userId) {
        verifierEstAdmin(tontineId, userId);
        Tirage tirage = tirageRepository.findByIdAndTontineId(tirageId, tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tirage non trouvé"));

        return tirageLitigeRepository.findByTirageIdOrderByCreatedAtDesc(tirage.getId())
                .stream().map(this::toTirageLitigeResponse).collect(Collectors.toList());
    }

    @Override
    @CacheEvict(value = "statistiques", key = "#tontineId")
    public TirageLitigeResponse resoudreLitige(Long tontineId, Long tirageId, Long litigeId,
                                                boolean confirme, String commentaire, Long adminId) {
        verifierEstAdmin(tontineId, adminId);

        Tirage tirage = tirageRepository.findByIdAndTontineId(tirageId, tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tirage non trouvé"));
        TirageLitige litige = tirageLitigeRepository.findByIdAndTirageId(litigeId, tirage.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Signalement non trouvé"));

        if (litige.getStatut() != LitigeStatut.EN_COURS) {
            throw new BadRequestException("Ce signalement a déjà été traité");
        }

        Utilisateur admin = utilisateurRepository.findById(adminId).orElseThrow();
        litige.setStatut(confirme ? LitigeStatut.CONFIRME : LitigeStatut.REJETE);
        litige.setResoluPar(admin);
        litige.setResoluLe(LocalDateTime.now());
        litige.setResolutionCommentaire(commentaire);
        litige = tirageLitigeRepository.save(litige);

        tirage.setEnLitige(false);

        Tontine tontine = tirage.getTontine();
        if (confirme) {
            // Le signalement était fondé : on invalide le tirage en le faisant
            // retomber dans le même état qu'un déclin — réutilise le flux de
            // remplacement (Phase 3) plutôt que de réinventer une annulation.
            tirage.setStatutAcceptation(TirageAcceptationStatut.DECLINE);
            MembreTontine beneficiaire = tirage.getBeneficiaire();
            beneficiaire.setACagnotteSurCycleActuel(false);
            membreRepository.save(beneficiaire);

            notificationService.creerNotification(tontine.getCreateur(), tontine,
                    "⚠️ Signalement confirmé — " + tontine.getNom(),
                    "Le tirage du cycle " + tirage.getNumeroCycle() + " a été invalidé après vérification. "
                            + "Choisissez un remplaçant.",
                    NotificationType.TIRAGE_SIGNALE);
        } else {
            notificationService.creerNotification(litige.getSignalePar(), tontine,
                    "Signalement examiné — " + tontine.getNom(),
                    "Votre signalement sur le tirage du cycle " + tirage.getNumeroCycle()
                            + " a été examiné et rejeté."
                            + (commentaire != null && !commentaire.isBlank() ? " Note : " + commentaire : ""),
                    NotificationType.TIRAGE_SIGNALE);
        }
        tirageRepository.save(tirage);

        log.info("Litige {} résolu (confirme={}) par adminId={} pour tirage {}", litigeId, confirme, adminId, tirageId);
        return toTirageLitigeResponse(litige);
    }

    private TirageLitigeResponse toTirageLitigeResponse(TirageLitige l) {
        return TirageLitigeResponse.builder()
                .id(l.getId()).tirageId(l.getTirage().getId())
                .signaleParId(l.getSignalePar().getId())
                .signaleParNom(l.getSignalePar().getPrenom() + " " + l.getSignalePar().getNom())
                .motif(l.getMotif()).statut(l.getStatut())
                .resoluParNom(l.getResoluPar() != null ? l.getResoluPar().getPrenom() + " " + l.getResoluPar().getNom() : null)
                .resoluLe(l.getResoluLe()).resolutionCommentaire(l.getResolutionCommentaire())
                .createdAt(l.getCreatedAt()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TirageResponse> getHistoriqueTirages(Long tontineId, Long userId) {
        verifierAccesTontine(tontineId, userId);
        return tirageRepository.findByTontineIdOrderByNumeroCycleAsc(tontineId)
                .stream().map(this::toTirageResponse).collect(Collectors.toList());
    }

    // ── Statistiques — CRÉATEUR UNIQUEMENT ───────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "statistiques", key = "#tontineId")
    public StatistiquesResponse getStatistiques(Long tontineId, Long userId) {
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));

        verifierEstAdmin(tontineId, userId);
        return construireStatistiques(tontine);
    }

    /** Calcul des statistiques d'une tontine — sans contrôle d'accès (fait par l'appelant). */
    private StatistiquesResponse construireStatistiques(Tontine tontine) {
        Long tontineId = tontine.getId();
        BigDecimal totalCollecte   = cotisationRepository.sumMontantPayeByTontineId(tontineId);
        BigDecimal totalDistribue  = tirageRepository.sumMontantDistribueByTontineId(tontineId);
        List<MembreTontine> membres = membreRepository.findByTontineIdAndActifTrue(tontineId);

        List<StatistiquesResponse.MembreStatResponse> statsParMembre = membres.stream().map(m -> {
            BigDecimal total = cotisationRepository.sumMontantPayeByMembreId(m.getId());
            int retards = m.getNombreRetards();
            int paiements = cotisationRepository.countPayesByMembreIdAndTontineId(m.getId(), tontineId);
            return StatistiquesResponse.MembreStatResponse.builder()
                    .membreId(m.getId())
                    .nomComplet(m.getUtilisateur().getPrenom() + " " + m.getUtilisateur().getNom())
                    .avatarId(m.getUtilisateur().getAvatarId())
                    .totalCotise(total)
                    .nombrePaiements(paiements)
                    .nombreRetards(retards)
                    .aCagnotteSurCycleActuel(m.getACagnotteSurCycleActuel())
                    .build();
        }).collect(Collectors.toList());

        int retards = membres.stream().mapToInt(MembreTontine::getNombreRetards).sum();
        int totalPossible = membres.size() * Math.max(1, tontine.getCycleActuel() - 1);
        double tauxPonctualite = totalPossible == 0 ? 100.0 : Math.max(0.0, (1 - (double) retards / totalPossible) * 100);

        return StatistiquesResponse.builder()
                .tontineId(tontineId)
                .tontineNom(tontine.getNom())
                .totalCollecte(totalCollecte)
                .totalDistribue(totalDistribue)
                .nombreCyclesCompletes(tontine.getStatut() == com.tontine.enums.TontineStatus.TERMINEE
                        ? tontine.getCycleActuel()
                        : Math.max(0, tontine.getCycleActuel() - 1))
                .nombreMembresActifs(membres.size())
                .nombrePaiementsEnRetard(retards)
                .tauxPonctualite(Math.round(tauxPonctualite * 10.0) / 10.0)
                .statsParMembre(statsParMembre)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StatsGlobalesResponse getStatistiquesGlobales(Long userId) {
        // Toutes les tontines de l'utilisateur en une requête (mêmes données que getMesTontines)
        List<Tontine> tontines = tontineRepository
                .findAllByMembreId(userId, PageRequest.of(0, 100)).getContent();

        List<StatsGlobalesResponse.TontineStatsBloc> blocs = tontines.stream().map(tontine -> {
            boolean estAdmin = membreRepository
                    .findByUtilisateurIdAndTontineId(userId, tontine.getId())
                    .map(m -> m.getRoleMembreTontine() == MembreTontineRole.CREATEUR
                            || m.getRoleMembreTontine() == MembreTontineRole.ADMIN)
                    .orElse(false);

            List<CotisationResponse> cotisations = cotisationRepository
                    .findByTontineIdOrderByCreatedAtDesc(tontine.getId(), PageRequest.of(0, 200))
                    .getContent().stream().map(this::toCotisationResponse).collect(Collectors.toList());

            return StatsGlobalesResponse.TontineStatsBloc.builder()
                    .tontineId(tontine.getId())
                    .statistiques(estAdmin ? construireStatistiques(tontine) : null)
                    .cotisations(cotisations)
                    .build();
        }).collect(Collectors.toList());

        return StatsGlobalesResponse.builder().tontines(blocs).build();
    }

    // ── Contrôles d'accès ─────────────────────────────────────────────────────

    /**
     * Vérifie que l'utilisateur est membre de la tontine (accès de base).
     * Lève ForbiddenException si non membre.
     */
    private void verifierAccesTontine(Long tontineId, Long userId) {
        if (!membreRepository.existsByUtilisateurIdAndTontineId(userId, tontineId)) {
            throw new ForbiddenException("Accès refusé. Vous n'êtes pas membre de cette tontine.");
        }
    }

    /**
     * Vérifie que l'utilisateur est admin ou créateur.
     */
    private void verifierEstAdmin(Long tontineId, Long userId) {
        MembreTontine m = membreRepository.findByUtilisateurIdAndTontineId(userId, tontineId)
                .orElseThrow(() -> new ForbiddenException("Vous n'êtes pas membre de cette tontine"));
        if (m.getRoleMembreTontine() == MembreTontineRole.MEMBRE) {
            throw new ForbiddenException("Action réservée aux administrateurs de la tontine");
        }
    }

    /**
     * Vérifie que l'utilisateur EST le créateur.
     * Cotisations, tirage et statistiques sont réservés au créateur.
     */
    private void verifierEstCreateur(Tontine tontine, Long userId) {
        if (!tontine.getCreateur().getId().equals(userId)) {
            throw new ForbiddenException("Action réservée au créateur de cette tontine.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MembreTontine choisirBeneficiaire(Tontine tontine, TirageRequest request) {
        List<MembreTontine> eligibles = membreRepository
                .findEligiblesPourTirage(tontine.getId());

        if (eligibles.isEmpty()) throw new BadRequestException("Tous les membres ont déjà reçu leur cagnotte ce cycle");

        return switch (tontine.getTypeTirage()) {
            case RANDOM  -> eligibles.get(SECURE_RANDOM.nextInt(eligibles.size()));
            case ROTATIF -> eligibles.stream()
                    .min(Comparator.comparingInt(m -> m.getOrdreTour() != null ? m.getOrdreTour() : Integer.MAX_VALUE))
                    .orElseThrow();
            case MANUEL  -> {
                if (request.getBeneficiaireId() == null)
                    throw new BadRequestException("Sélectionnez un bénéficiaire pour le tirage manuel");
                yield eligibles.stream()
                        .filter(m -> m.getId().equals(request.getBeneficiaireId()))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException("Bénéficiaire non éligible"));
            }
        };
    }

    private LocalDate calculerProchainCycle(Tontine tontine) {
        LocalDate base = tontine.getDateProchainCycle() != null ? tontine.getDateProchainCycle() : LocalDate.now();
        return switch (tontine.getFrequence()) {
            case HEBDOMADAIRE -> base.plusWeeks(1);
            case BIMENSUEL    -> base.plusWeeks(2);
            case MENSUEL      -> base.plusMonths(1);
        };
    }

    private void notifierTirage(Tontine tontine, MembreTontine beneficiaire, BigDecimal cagnotte) {
        membreRepository.findByTontineIdAndActifTrue(tontine.getId()).forEach(m -> {
            boolean estBenef = m.getId().equals(beneficiaire.getId());
            if (estBenef) {
                notificationService.creerNotification(m.getUtilisateur(), tontine,
                        "🎉 C'est votre tour !",
                        "Félicitations ! Vous recevrez " + cagnotte + " " + tontine.getDevise()
                                + " pour la tontine « " + tontine.getNom() + " ».",
                        NotificationType.TIRAGE_BENEFICIAIRE);
            } else {
                notificationService.creerNotification(m.getUtilisateur(), tontine,
                        "🎲 Tirage effectué — " + tontine.getNom(),
                        beneficiaire.getUtilisateur().getPrenom() + " " + beneficiaire.getUtilisateur().getNom()
                                + " a été désigné(e) bénéficiaire et recevra "
                                + cagnotte + " " + tontine.getDevise() + ".",
                        NotificationType.TIRAGE_EFFECTUE);
            }
        });
    }

    // ── Mappers (batch — sans N+1) ────────────────────────────────────────────

    private TontineResponse toResponseBatch(
            Tontine t,
            Map<Long, BigDecimal> totauxParTontine,
            Map<Long, BigDecimal> distribuesParTontine,
            Map<Long, List<MembreTontine>> membresParTontine,
            Map<Long, BigDecimal> totauxParMembre,
            Map<Long, Set<Long>> payesParTontine,
            Long currentUserId) {

        BigDecimal total     = totauxParTontine.getOrDefault(t.getId(), BigDecimal.ZERO);
        BigDecimal distribue = distribuesParTontine.getOrDefault(t.getId(), BigDecimal.ZERO);
        List<MembreTontine> membresRaw = membresParTontine.getOrDefault(t.getId(), Collections.emptyList());
        Set<Long> payesCeCycle = payesParTontine.getOrDefault(t.getId(), Collections.emptySet());

        // Calcul depuis les membres déjà chargés (évite l'accès lazy sur t.getCreateur())
        boolean estCreateur = currentUserId != null && membresRaw.stream()
                .anyMatch(m -> m.getUtilisateur().getId().equals(currentUserId)
                        && m.getRoleMembreTontine() == MembreTontineRole.CREATEUR);

        List<MembreResponse> membres = membresRaw.stream()
                .map(m -> toMembreResponseBatch(m, totauxParMembre, payesCeCycle))
                .collect(Collectors.toList());

        return TontineResponse.builder()
                .id(t.getId()).nom(t.getNom()).description(t.getDescription())
                .montantContribution(t.getMontantContribution()).devise(t.getDevise())
                .frequence(t.getFrequence()).typeTirage(t.getTypeTirage()).statut(t.getStatut())
                .dateDebut(t.getDateDebut()).dateProchainCycle(t.getDateProchainCycle())
                .tirageHeure(t.getTirageHeure())
                .cycleActuel(t.getCycleActuel()).nombreMaxMembres(t.getNombreMaxMembres())
                .codeInvitation(estCreateur ? t.getCodeInvitation() : null)
                .totalCollecte(total)
                .totalDistribue(distribue)
                .estCreateur(estCreateur)
                .nombreMembresActifs((int) membres.stream().filter(m -> Boolean.TRUE.equals(m.getActif())).count())
                .commissionPourcent(t.getCommissionPourcent())
                .montantAmende(t.getMontantAmende())
                .numeroMtnMomo(t.getNumeroMtnMomo())
                .numeroOrangeMomo(t.getNumeroOrangeMomo())
                .createdAt(t.getCreatedAt()).membres(membres).build();
    }

    private MembreResponse toMembreResponseBatch(
            MembreTontine m,
            Map<Long, BigDecimal> totauxParMembre,
            Set<Long> membresAyantPaye) {

        BigDecimal total = totauxParMembre.getOrDefault(m.getId(), BigDecimal.ZERO);
        boolean aPaye = membresAyantPaye.contains(m.getId());
        String roleStr = (m.getRoleMembreTontine() == MembreTontineRole.MEMBRE) ? "MEMBRE" : "ADMIN";
        boolean effectifActif = m.getRoleMembreTontine() == MembreTontineRole.CREATEUR
                || m.getStatutMembre() == MembreStatut.ACTIF;

        return MembreResponse.builder()
                .membreId(m.getId()).utilisateurId(m.getUtilisateur().getId())
                .nom(m.getUtilisateur().getNom()).prenom(m.getUtilisateur().getPrenom())
                .telephone(m.getUtilisateur().getTelephone())
                .avatarId(m.getUtilisateur().getAvatarId())
                .role(roleStr)
                .ordreTour(m.getOrdreTour()).actif(effectifActif)
                .statutMembre(m.getStatutMembre() != null ? m.getStatutMembre().name() : "ACTIF")
                .aCagnotteSurCycleActuel(m.getACagnotteSurCycleActuel())
                .nombreRetards(m.getNombreRetards()).totalCotise(total).aPaye(aPaye)
                .dateAdhesion(m.getDateAdhesion()).build();
    }

    // ── Mappers (single entity) ───────────────────────────────────────────────

    private TontineResponse toResponse(Tontine t, Long userId) {
        BigDecimal total = cotisationRepository.sumMontantPayeByTontineId(t.getId());
        List<MembreTontine> membresRaw = membreRepository
                .findByTontineIdAndStatutMembreNot(t.getId(), MembreStatut.RETIRE);
        List<MembreResponse> membres = membresRaw.stream()
                .map(m -> toMembreResponse(m, t)).collect(Collectors.toList());

        boolean estCreateur = userId != null && membresRaw.stream()
                .anyMatch(m -> m.getUtilisateur().getId().equals(userId)
                        && m.getRoleMembreTontine() == MembreTontineRole.CREATEUR);

        BigDecimal distribue = tirageRepository.sumMontantDistribueByTontineId(t.getId());
        return TontineResponse.builder()
                .id(t.getId()).nom(t.getNom()).description(t.getDescription())
                .montantContribution(t.getMontantContribution()).devise(t.getDevise())
                .frequence(t.getFrequence()).typeTirage(t.getTypeTirage()).statut(t.getStatut())
                .dateDebut(t.getDateDebut()).dateProchainCycle(t.getDateProchainCycle())
                .tirageHeure(t.getTirageHeure())
                .cycleActuel(t.getCycleActuel()).nombreMaxMembres(t.getNombreMaxMembres())
                .codeInvitation(estCreateur ? t.getCodeInvitation() : null)
                .totalCollecte(total)
                .totalDistribue(distribue)
                .estCreateur(estCreateur)
                .nombreMembresActifs((int) membres.stream().filter(m -> Boolean.TRUE.equals(m.getActif())).count())
                .commissionPourcent(t.getCommissionPourcent())
                .montantAmende(t.getMontantAmende())
                .numeroMtnMomo(t.getNumeroMtnMomo())
                .numeroOrangeMomo(t.getNumeroOrangeMomo())
                .createdAt(t.getCreatedAt()).membres(membres).build();
    }

    private MembreResponse toMembreResponse(MembreTontine m, Tontine tontine) {
        BigDecimal total = cotisationRepository.sumMontantPayeByMembreId(m.getId());
        boolean aPaye = cotisationRepository
                .findByMembreIdAndTontineIdAndNumeroCycle(m.getId(), tontine.getId(), tontine.getCycleActuel())
                .filter(c -> c.getStatut() == PaiementStatus.PAYE).isPresent();

        // CREATEUR et ADMIN → "ADMIN" (unifié pour le client mobile)
        String roleStr = (m.getRoleMembreTontine() == MembreTontineRole.MEMBRE) ? "MEMBRE" : "ADMIN";

        // Le créateur est toujours ACTIF — garde défensive contre données incohérentes en DB
        boolean effectifActif = m.getRoleMembreTontine() == MembreTontineRole.CREATEUR
                || m.getStatutMembre() == MembreStatut.ACTIF;

        return MembreResponse.builder()
                .membreId(m.getId()).utilisateurId(m.getUtilisateur().getId())
                .nom(m.getUtilisateur().getNom()).prenom(m.getUtilisateur().getPrenom())
                .telephone(m.getUtilisateur().getTelephone())
                .avatarId(m.getUtilisateur().getAvatarId())
                .role(roleStr)
                .ordreTour(m.getOrdreTour()).actif(effectifActif)
                .statutMembre(m.getStatutMembre() != null ? m.getStatutMembre().name() : "ACTIF")
                .aCagnotteSurCycleActuel(m.getACagnotteSurCycleActuel())
                .nombreRetards(m.getNombreRetards()).totalCotise(total).aPaye(aPaye)
                .dateAdhesion(m.getDateAdhesion()).build();
    }

    private CotisationResponse toCotisationResponse(Cotisation c) {
        return CotisationResponse.builder()
                .id(c.getId()).tontineId(c.getTontine().getId()).tontineNom(c.getTontine().getNom())
                .membreId(c.getMembre().getId())
                .membreNom(c.getMembre().getUtilisateur().getPrenom() + " " + c.getMembre().getUtilisateur().getNom())
                .montant(c.getMontant()).montantAmende(c.getMontantAmende())
                .numeroCycle(c.getNumeroCycle()).statut(c.getStatut())
                .estEnRetard(c.getEstEnRetard())
                .dateEcheance(c.getDateEcheance()).datePaiement(c.getDatePaiement())
                .modePaiement(c.getModePaiement()).referenceTransaction(c.getReferenceTransaction())
                .createdAt(c.getCreatedAt()).build();
    }

    private TirageResponse toTirageResponse(Tirage t) {
        return TirageResponse.builder()
                .id(t.getId()).tontineId(t.getTontine().getId()).tontineNom(t.getTontine().getNom())
                .beneficiaireId(t.getBeneficiaire().getId())
                .beneficiaireNom(t.getBeneficiaire().getUtilisateur().getPrenom() + " " + t.getBeneficiaire().getUtilisateur().getNom())
                .beneficiaireAvatarId(t.getBeneficiaire().getUtilisateur().getAvatarId())
                .numeroCycle(t.getNumeroCycle()).montantDistribue(t.getMontantDistribue())
                .commissionPrelevee(t.getCommissionPrelevee() != null ? t.getCommissionPrelevee() : BigDecimal.ZERO)
                .methodeTirage(t.getMethodeTirage()).dateTirage(t.getDateTirage())
                .confirme(t.getConfirme())
                .statutAcceptation(t.getStatutAcceptation())
                .dateExpirationReponse(t.getDateExpirationReponse())
                .enLitige(t.getEnLitige())
                .createdAt(t.getCreatedAt()).build();
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId) {
        List<MembreTontine> memberships = membreRepository.findByUtilisateurId(userId);

        List<MembreTontine> actifs = memberships.stream()
                .filter(m -> Boolean.TRUE.equals(m.getActif())
                        && m.getTontine().getStatut() == TontineStatus.ACTIVE)
                .collect(Collectors.toList());

        int nombreActives    = actifs.size();
        int jeCreateur       = (int) actifs.stream()
                .filter(m -> m.getTontine().getCreateur().getId().equals(userId))
                .count();
        int retardsTotaux    = memberships.stream()
                .mapToInt(m -> m.getNombreRetards() != null ? m.getNombreRetards() : 0)
                .sum();
        BigDecimal totalCotise = cotisationRepository.sumMontantPayeByUtilisateurId(userId);

        // Batch: nombre de membres actifs par tontine (1 query au lieu de N)
        List<Long> actifsTontineIds = actifs.stream().map(m -> m.getTontine().getId()).distinct().toList();
        Map<Long, Long> nbMembresParTontine = actifsTontineIds.isEmpty()
                ? Collections.emptyMap()
                : membreRepository.countActifGroupByTontineIds(actifsTontineIds).stream()
                        .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        // Total membres dans toutes les tontines dont l'utilisateur est admin
        long totalMembres = actifs.stream()
                .filter(m -> m.getTontine().getCreateur().getId().equals(userId))
                .map(m -> m.getTontine().getId())
                .distinct()
                .mapToLong(id -> nbMembresParTontine.getOrDefault(id, 0L))
                .sum();

        // Prochain tirage : tontine active avec la date de prochain cycle la plus proche
        DashboardResponse.ProchainTirageInfo prochainTirage = actifs.stream()
                .filter(m -> m.getTontine().getDateProchainCycle() != null
                        && !m.getTontine().getDateProchainCycle().isBefore(LocalDate.now()))
                .min(Comparator.comparing(m -> m.getTontine().getDateProchainCycle()))
                .map(m -> {
                    Tontine t = m.getTontine();
                    long nbMembres = nbMembresParTontine.getOrDefault(t.getId(), 0L);
                    BigDecimal cagnotte = t.getMontantContribution()
                            .multiply(BigDecimal.valueOf(nbMembres));
                    return DashboardResponse.ProchainTirageInfo.builder()
                            .tontineId(t.getId())
                            .tontineNom(t.getNom())
                            .dateTirage(t.getDateProchainCycle())
                            .montantCagnotte(cagnotte)
                            .build();
                })
                .orElse(null);

        // Cotisations dues : tontines actives où le cycle actuel n'est pas encore payé
        List<DashboardResponse.CotisationDueInfo> dues = actifs.stream()
                .filter(m -> {
                    Tontine t = m.getTontine();
                    return cotisationRepository
                            .findByMembreIdAndTontineIdAndNumeroCycle(m.getId(), t.getId(), t.getCycleActuel())
                            .map(c -> c.getStatut() != PaiementStatus.PAYE)
                            .orElse(true); // pas de cotisation = due
                })
                .map(m -> {
                    Tontine t = m.getTontine();
                    return DashboardResponse.CotisationDueInfo.builder()
                            .tontineId(t.getId())
                            .tontineNom(t.getNom())
                            .montant(t.getMontantContribution())
                            .devise(t.getDevise())
                            .dateEcheance(t.getDateProchainCycle())
                            .cycle(t.getCycleActuel())
                            .build();
                })
                .collect(Collectors.toList());

        return DashboardResponse.builder()
                .nombreTontinesActives(nombreActives)
                .tontinesOuJeSuisCreateur(jeCreateur)
                .totalCotise(totalCotise)
                .mesRetardsTotaux(retardsTotaux)
                .totalMembres(totalMembres)
                .prochainTirage(prochainTirage)
                .cotisationsEnAttente(dues)
                .build();
    }

    // ── Export CSV ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCotisationsCsv(Long tontineId, Long userId) {
        verifierEstAdmin(tontineId, userId);

        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResourceNotFoundException("Tontine non trouvée"));

        List<Cotisation> cotisations = cotisationRepository.findAllByTontineIdForExport(tontineId);

        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF'); // BOM UTF-8 — compatibilité Excel
        csv.append("Cycle,Nom,Prénom,Téléphone,Montant (").append(tontine.getDevise())
           .append("),Statut,Date échéance,Date paiement,Mode paiement,En retard,Commentaire\n");

        for (Cotisation c : cotisations) {
            Utilisateur u = c.getMembre().getUtilisateur();
            csv.append(c.getNumeroCycle()).append(',')
               .append(escapeCsv(u.getNom())).append(',')
               .append(escapeCsv(u.getPrenom())).append(',')
               .append(escapeCsv(u.getTelephone())).append(',')
               .append(c.getMontant()).append(',')
               .append(c.getStatut().name()).append(',')
               .append(c.getDateEcheance() != null ? c.getDateEcheance() : "").append(',')
               .append(c.getDatePaiement() != null ? c.getDatePaiement() : "").append(',')
               .append(escapeCsv(c.getModePaiement() != null ? c.getModePaiement() : "")).append(',')
               .append(Boolean.TRUE.equals(c.getEstEnRetard()) ? "Oui" : "Non").append(',')
               .append(escapeCsv(c.getCommentaire() != null ? c.getCommentaire() : "")).append('\n');
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private void verifierWalletMobileMoney(Long userId) {
        boolean aWallet = compteWalletRepository
                .existsByUtilisateurIdAndOperateurInAndTelephoneIsNotNull(
                        userId,
                        List.of(PaiementMode.MTN_MOBILE_MONEY, PaiementMode.ORANGE_MONEY));
        if (!aWallet) {
            throw new com.tontine.exception.WalletRequisException();
        }
    }

    private String genererCodeInvitation() {
        String code;
        do { code = UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
        while (tontineRepository.findByCodeInvitation(code).isPresent());
        return code;
    }

    // ── Ordre de passage (rotatif) ────────────────────────────────────────────

    @Override
    @Transactional
    public void modifierOrdrePassage(Long tontineId, OrdrePassageRequest request, Long adminId) {
        Tontine tontine = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new RuntimeException("Tontine introuvable"));

        verifierCreateur(tontine, adminId);

        if (tontine.getTypeTirage() != TirageType.ROTATIF) {
            throw new RuntimeException("L'ordre de passage ne s'applique qu'aux tontines rotatives");
        }

        List<MembreTontine> membres = membreRepository.findByTontineId(tontineId);

        // Étape 1 : mettre ordreTour à NULL pour lever la contrainte d'unicité
        membres.forEach(m -> m.setOrdreTour(null));
        membreRepository.saveAll(membres);
        membreRepository.flush();

        // Étape 2 : appliquer le nouvel ordre
        Map<Long, MembreTontine> index = membres.stream()
                .collect(java.util.stream.Collectors.toMap(MembreTontine::getId, m -> m));

        for (OrdrePassageRequest.OrdreMembreDto dto : request.getMembres()) {
            MembreTontine m = index.get(dto.getMembreId());
            if (m != null) m.setOrdreTour(dto.getOrdreTour());
        }
        membreRepository.saveAll(membres);
    }

    private void verifierCreateur(Tontine tontine, Long userId) {
        boolean estCreateur = tontine.getMembres().stream()
                .anyMatch(m -> m.getUtilisateur().getId().equals(userId)
                        && m.getRoleMembreTontine() == MembreTontineRole.CREATEUR);
        if (!estCreateur) throw new RuntimeException("Action réservée au créateur");
    }
}