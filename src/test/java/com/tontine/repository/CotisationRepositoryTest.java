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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:cotisationdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@Import(JpaAuditingConfig.class)
class CotisationRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private CotisationRepository cotisationRepository;

    private Utilisateur u1, u2;
    private Tontine t1, t2;
    private MembreTontine m1, m2, m3;

    @BeforeEach
    void setUp() {
        u1 = em.persist(Utilisateur.builder()
                .nom("Kamga").prenom("Jean").telephone("699000001").role(Role.USER).build());
        u2 = em.persist(Utilisateur.builder()
                .nom("Talla").prenom("Alice").telephone("699000002").role(Role.USER).build());

        t1 = em.persist(Tontine.builder()
                .nom("Tontine A").montantContribution(new BigDecimal("5000"))
                .frequence(FrequenceType.MENSUEL).typeTirage(TirageType.RANDOM)
                .createur(u1).codeInvitation("CODE0001").cycleActuel(1).nombreMaxMembres(10).build());
        t2 = em.persist(Tontine.builder()
                .nom("Tontine B").montantContribution(new BigDecimal("10000"))
                .frequence(FrequenceType.MENSUEL).typeTirage(TirageType.ROTATIF)
                .createur(u2).codeInvitation("CODE0002").cycleActuel(2).nombreMaxMembres(10).build());

        m1 = em.persist(MembreTontine.builder()
                .utilisateur(u1).tontine(t1).roleMembreTontine(MembreTontineRole.CREATEUR)
                .statutMembre(MembreStatut.ACTIF).actif(true).build());
        m2 = em.persist(MembreTontine.builder()
                .utilisateur(u2).tontine(t1).roleMembreTontine(MembreTontineRole.MEMBRE)
                .statutMembre(MembreStatut.ACTIF).actif(true).build());
        m3 = em.persist(MembreTontine.builder()
                .utilisateur(u1).tontine(t2).roleMembreTontine(MembreTontineRole.CREATEUR)
                .statutMembre(MembreStatut.ACTIF).actif(true).build());

        // Cotisations cycle 1 de t1
        em.persist(Cotisation.builder()
                .tontine(t1).membre(m1).montant(new BigDecimal("5000"))
                .numeroCycle(1).statut(PaiementStatus.PAYE).datePaiement(LocalDate.now()).build());
        em.persist(Cotisation.builder()
                .tontine(t1).membre(m2).montant(new BigDecimal("5000"))
                .numeroCycle(1).statut(PaiementStatus.PAYE).datePaiement(LocalDate.now()).build());
        // Cotisation non payée cycle 2
        em.persist(Cotisation.builder()
                .tontine(t1).membre(m1).montant(new BigDecimal("5000"))
                .numeroCycle(2).statut(PaiementStatus.EN_ATTENTE).build());
        // Cotisation dans t2
        em.persist(Cotisation.builder()
                .tontine(t2).membre(m3).montant(new BigDecimal("10000"))
                .numeroCycle(1).statut(PaiementStatus.PAYE).datePaiement(LocalDate.now()).build());

        em.flush();
    }

    // ── sumMontantPayeGroupByTontineIds ───────────────────────────────────────

    @Test
    void sumMontantPayeGroupByTontineIds_retourne_totaux_corrects() {
        List<Object[]> results = cotisationRepository
                .sumMontantPayeGroupByTontineIds(List.of(t1.getId(), t2.getId()));

        assertThat(results).hasSize(2);

        // t1 : 5000 + 5000 = 10000 (cycle 2 EN_ATTENTE n'est pas compté)
        results.stream()
                .filter(r -> r[0].equals(t1.getId()))
                .findFirst()
                .ifPresent(r -> assertThat((BigDecimal) r[1])
                        .isEqualByComparingTo(new BigDecimal("10000")));

        // t2 : 10000
        results.stream()
                .filter(r -> r[0].equals(t2.getId()))
                .findFirst()
                .ifPresent(r -> assertThat((BigDecimal) r[1])
                        .isEqualByComparingTo(new BigDecimal("10000")));
    }

    @Test
    void sumMontantPayeGroupByTontineIds_liste_ids_vide_retourne_vide() {
        List<Object[]> results = cotisationRepository
                .sumMontantPayeGroupByTontineIds(List.of());
        assertThat(results).isEmpty();
    }

    @Test
    void sumMontantPayeGroupByTontineIds_id_sans_cotisation_absent_du_resultat() {
        List<Object[]> results = cotisationRepository
                .sumMontantPayeGroupByTontineIds(List.of(9999L));
        assertThat(results).isEmpty();
    }

    // ── sumMontantPayeGroupByMembreIds ────────────────────────────────────────

    @Test
    void sumMontantPayeGroupByMembreIds_retourne_totaux_par_membre() {
        List<Object[]> results = cotisationRepository
                .sumMontantPayeGroupByMembreIds(List.of(m1.getId(), m2.getId(), m3.getId()));

        assertThat(results).hasSize(3);

        // m1 : 5000 (t1 cycle1) ; la EN_ATTENTE ne compte pas
        results.stream()
                .filter(r -> r[0].equals(m1.getId()))
                .findFirst()
                .ifPresent(r -> assertThat((BigDecimal) r[1])
                        .isEqualByComparingTo(new BigDecimal("5000")));

        // m2 : 5000
        results.stream()
                .filter(r -> r[0].equals(m2.getId()))
                .findFirst()
                .ifPresent(r -> assertThat((BigDecimal) r[1])
                        .isEqualByComparingTo(new BigDecimal("5000")));
    }

    // ── findMembreIdsAyantPayePourCycle ───────────────────────────────────────

    @Test
    void findMembreIdsAyantPayePourCycle_retourne_membres_ayant_paye() {
        Set<Long> ids = cotisationRepository
                .findMembreIdsAyantPayePourCycle(t1.getId(), 1);

        assertThat(ids).containsExactlyInAnyOrder(m1.getId(), m2.getId());
    }

    @Test
    void findMembreIdsAyantPayePourCycle_exclut_statut_en_attente() {
        Set<Long> ids = cotisationRepository
                .findMembreIdsAyantPayePourCycle(t1.getId(), 2);

        assertThat(ids).isEmpty(); // cycle 2 est EN_ATTENTE
    }

    @Test
    void findMembreIdsAyantPayePourCycle_cycle_inexistant_retourne_vide() {
        Set<Long> ids = cotisationRepository
                .findMembreIdsAyantPayePourCycle(t1.getId(), 99);

        assertThat(ids).isEmpty();
    }

    // ── countPayesByMembreIdAndTontineId ──────────────────────────────────────

    @Test
    void countPayesByMembreIdAndTontineId_compte_uniquement_paye() {
        // m1 a 1 cotisation PAYEE et 1 EN_ATTENTE dans t1
        int count = cotisationRepository.countPayesByMembreIdAndTontineId(m1.getId(), t1.getId());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void countPayesByMembreIdAndTontineId_retourne_zero_si_aucune() {
        int count = cotisationRepository.countPayesByMembreIdAndTontineId(m2.getId(), t2.getId());
        assertThat(count).isEqualTo(0);
    }

    // ── sumMontantPayeByTontineId (query existante) ───────────────────────────

    @Test
    void sumMontantPayeByTontineId_ignore_statut_en_attente() {
        BigDecimal total = cotisationRepository.sumMontantPayeByTontineId(t1.getId());
        // 5000 + 5000 = 10000 (la EN_ATTENTE n'est pas comptée)
        assertThat(total).isEqualByComparingTo(new BigDecimal("10000"));
    }

    @Test
    void sumMontantPayeByTontineId_tontine_sans_cotisations_retourne_zero() {
        Tontine vide = em.persist(Tontine.builder()
                .nom("Vide").montantContribution(new BigDecimal("1000"))
                .frequence(FrequenceType.HEBDOMADAIRE).typeTirage(TirageType.MANUEL)
                .createur(u1).codeInvitation("VIDE0001").cycleActuel(1).nombreMaxMembres(5).build());
        em.flush();

        BigDecimal total = cotisationRepository.sumMontantPayeByTontineId(vide.getId());
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
