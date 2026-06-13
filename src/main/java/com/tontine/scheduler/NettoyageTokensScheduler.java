package com.tontine.scheduler;

import com.tontine.entity.MembreTontine;
import com.tontine.enums.MembreStatut;
import com.tontine.repository.MembreTontineRepository;
import com.tontine.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Purge nightly des tokens expirés dans la table utilisateurs.
 * Évite l'accumulation silencieuse de hash périmés qui ne seront plus jamais utilisés.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NettoyageTokensScheduler {

    private final UtilisateurRepository  utilisateurRepository;
    private final MembreTontineRepository membreRepository;

    /** Exécution à 02h00 chaque nuit — fenêtre de faible charge. */
    @Scheduled(cron = "0 0 2 * * ?")
    @SchedulerLock(name = "nettoyageTokens", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    @Transactional
    public void purgerTokensExpires() {
        LocalDateTime maintenant = LocalDateTime.now();

        int otpPurges = utilisateurRepository.purgerOtpExpires(maintenant);
        int refreshPurges = utilisateurRepository.purgerRefreshTokensExpires(maintenant);

        // Expirer les invitations EN_ATTENTE non acceptées après 24h
        LocalDateTime limite24h = maintenant.minusHours(24);
        List<MembreTontine> invitationsExpirees = membreRepository.findInvitationsExpirees(limite24h);
        for (MembreTontine m : invitationsExpirees) {
            m.setStatutMembre(MembreStatut.RETIRE);
            membreRepository.save(m);
        }

        log.info("[Nettoyage] OTP expirés: {} | Refresh tokens expirés: {} | Invitations expirées: {}",
                otpPurges, refreshPurges, invitationsExpirees.size());
    }
}
