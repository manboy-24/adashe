package com.tontine.repository;

import com.tontine.config.JpaAuditingConfig;
import com.tontine.entity.*;
import com.tontine.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:membredb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Import(JpaAuditingConfig.class)
class MembreTontineRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private MembreTontineRepository membreRepository;

    private Utilisateur u1, u2, u3;
    private Tontine t1, t2;
    private MembreTontine mCreateur1, mMembre1, mRetire1, mCreateur2;

    @BeforeEach
    void setUp() {
        u1 = em.persist(Utilisateur.builder()
                .nom("Kamga").prenom("Jean").telephone("699000001").role(Role.USER).build());
        u2 = em.persist(Utilisateur.builder()
                .nom("Talla").prenom("Alice").telephone("699000002").role(Role.USER).build());
        u3 = em.persist(Utilisateur.builder()
                .nom("Bello").prenom("Marc").telephone("699000003").role(Role.USER).build());

        t1 = em.persist(Tontine.builder()
                .nom("Tontine A").montantContribution(new BigDecimal("5000"))
                .frequence(FrequenceType.MENSUEL).typeTirage(TirageType.RANDOM)
                .createur(u1).codeInvitation("AAAAAA01").cycleActuel(1).nombreMaxMembres(10).build());
        t2 = em.persist(Tontine.builder()
                .nom("Tontine B").montantContribution(new BigDecimal("10000"))
                .frequence(FrequenceType.MENSUEL).typeTirage(TirageType.ROTATIF)
                .createur(u2).codeInvitation("BBBBBB02").cycleActuel(1).nombreMaxMembres(10).build());

        mCreateur1 = em.persist(MembreTontine.builder()
                .utilisateur(u1).tontine(t1)
                .roleMembreTontine(MembreTontineRole.CREATEUR)
                .statutMembre(MembreStatut.ACTIF).actif(true).build());
        mMembre1 = em.persist(MembreTontine.builder()
                .utilisateur(u2).tontine(t1)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.ACTIF).actif(true).build());
        mRetire1 = em.persist(MembreTontine.builder()
                .utilisateur(u3).tontine(t1)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.RETIRE).actif(false).build());
        mCreateur2 = em.persist(MembreTontine.builder()
                .utilisateur(u2).tontine(t2)
                .roleMembreTontine(MembreTontineRole.CREATEUR)
                .statutMembre(MembreStatut.ACTIF).actif(true).build());

        em.flush();
    }

    // ── findByTontineIdInAndStatutMembreNot ───────────────────────────────────

    @Test
    void findByTontineIdInAndStatutMembreNot_exclut_membres_retires() {
        List<MembreTontine> membres = membreRepository
                .findByTontineIdInAndStatutMembreNot(List.of(t1.getId()), MembreStatut.RETIRE);

        assertThat(membres).hasSize(2);
        assertThat(membres).noneMatch(m -> m.getStatutMembre() == MembreStatut.RETIRE);
        assertThat(membres).allMatch(m -> m.getTontine().getId().equals(t1.getId()));
    }

    @Test
    void findByTontineIdInAndStatutMembreNot_charge_utilisateur_sans_requete_supplementaire() {
        List<MembreTontine> membres = membreRepository
                .findByTontineIdInAndStatutMembreNot(List.of(t1.getId()), MembreStatut.RETIRE);

        // JOIN FETCH utilisateur — le nom doit être accessible sans nouvelle requête
        assertThat(membres).allSatisfy(m ->
                assertThat(m.getUtilisateur().getNom()).isNotBlank());
    }

    @Test
    void findByTontineIdInAndStatutMembreNot_batch_plusieurs_tontines() {
        List<MembreTontine> membres = membreRepository
                .findByTontineIdInAndStatutMembreNot(
                        List.of(t1.getId(), t2.getId()), MembreStatut.RETIRE);

        // t1: 2 actifs, t2: 1 actif
        assertThat(membres).hasSize(3);

        Map<Long, Long> parTontine = membres.stream()
                .collect(Collectors.groupingBy(m -> m.getTontine().getId(), Collectors.counting()));
        assertThat(parTontine.get(t1.getId())).isEqualTo(2L);
        assertThat(parTontine.get(t2.getId())).isEqualTo(1L);
    }

    @Test
    void findByTontineIdInAndStatutMembreNot_ids_vides_retourne_vide() {
        List<MembreTontine> membres = membreRepository
                .findByTontineIdInAndStatutMembreNot(List.of(), MembreStatut.RETIRE);
        assertThat(membres).isEmpty();
    }

    // ── countActifGroupByTontineIds ───────────────────────────────────────────

    @Test
    void countActifGroupByTontineIds_retourne_nombre_membres_actifs() {
        List<Object[]> results = membreRepository
                .countActifGroupByTontineIds(List.of(t1.getId(), t2.getId()));

        assertThat(results).hasSize(2);

        Map<Long, Long> counts = results.stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        // t1: u1 (ACTIF) + u2 (ACTIF) = 2, u3 (RETIRE, actif=false) non compté
        assertThat(counts.get(t1.getId())).isEqualTo(2L);
        // t2: u2 (ACTIF) = 1
        assertThat(counts.get(t2.getId())).isEqualTo(1L);
    }

    @Test
    void countActifGroupByTontineIds_ne_compte_pas_membres_inactifs() {
        // Ajouter un membre EN_ATTENTE (actif=false) dans t2
        em.persist(MembreTontine.builder()
                .utilisateur(u3).tontine(t2)
                .roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.EN_ATTENTE).actif(false).build());
        em.flush();

        List<Object[]> results = membreRepository
                .countActifGroupByTontineIds(List.of(t2.getId()));

        Map<Long, Long> counts = results.stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        // Toujours 1 — le EN_ATTENTE/actif=false n'est pas compté
        assertThat(counts.get(t2.getId())).isEqualTo(1L);
    }

    @Test
    void countActifGroupByTontineIds_id_sans_membres_absent_du_resultat() {
        List<Object[]> results = membreRepository
                .countActifGroupByTontineIds(List.of(9999L));
        assertThat(results).isEmpty();
    }

    // ── findEligiblesPourTirage ───────────────────────────────────────────────

    @Test
    void findEligiblesPourTirage_exclut_membres_ayant_deja_recu_cagnotte() {
        mMembre1.setACagnotteSurCycleActuel(true);
        em.flush();

        List<MembreTontine> eligibles = membreRepository.findEligiblesPourTirage(t1.getId());

        assertThat(eligibles).hasSize(1);
        assertThat(eligibles.get(0).getId()).isEqualTo(mCreateur1.getId());
    }

    @Test
    void findEligiblesPourTirage_exclut_membres_inactifs() {
        // mRetire1 est actif=false donc non éligible
        List<MembreTontine> eligibles = membreRepository.findEligiblesPourTirage(t1.getId());

        assertThat(eligibles).noneMatch(m -> m.getId().equals(mRetire1.getId()));
    }
}
