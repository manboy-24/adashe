package com.tontine.config;

import com.tontine.enums.MembreStatut;
import com.tontine.enums.MembreTontineRole;
import com.tontine.repository.MembreTontineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrige les lignes MembreTontine incohérentes créées avant le correctif creerTontine().
 * Les créateurs avaient statutMembre=EN_ATTENTE/actif=false à cause du @Builder.Default.
 * S'exécute une seule fois au démarrage ; idempotent (ne touche que les lignes corrompues).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataRepairRunner implements ApplicationRunner {

    private final MembreTontineRepository membreRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var corrompus = membreRepository.findAll().stream()
                .filter(m -> m.getRoleMembreTontine() == MembreTontineRole.CREATEUR
                          && m.getStatutMembre() != MembreStatut.ACTIF)
                .toList();

        if (!corrompus.isEmpty()) {
            corrompus.forEach(m -> {
                m.setStatutMembre(MembreStatut.ACTIF);
                m.setActif(true);
            });
            membreRepository.saveAll(corrompus);
            log.info("[DataRepair] {} créateur(s) remis à ACTIF", corrompus.size());
        }
    }
}
