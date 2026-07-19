package com.tontine.service.impl;

import com.tontine.service.impl.ScoreFiabiliteServiceImpl.StatsMembre;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreFiabiliteServiceImplTest {

    private final ScoreFiabiliteServiceImpl service =
            new ScoreFiabiliteServiceImpl(null, null, null, null, null, null, null);

    private StatsMembre stats(int tontines, int payees, int retards, int litiges, int mois) {
        return new StatsMembre(tontines, payees, retards, BigDecimal.ZERO, litiges, mois);
    }

    @Test
    void nouveau_membre_sans_historique_score_neutre_40() {
        assertThat(service.calculerScore(stats(1, 0, 0, 0, 0))).isEqualTo(40);
    }

    @Test
    void membre_exemplaire_score_maximal() {
        // 24+ cotisations, aucun retard, aucun litige, 18+ mois → 40+25+20+15 = 100
        assertThat(service.calculerScore(stats(3, 30, 0, 0, 24))).isEqualTo(100);
    }

    @Test
    void retards_font_baisser_la_ponctualite() {
        int sansRetard = service.calculerScore(stats(1, 10, 0, 0, 6));
        int avecRetards = service.calculerScore(stats(1, 10, 5, 0, 6));
        assertThat(avecRetards).isLessThan(sansRetard);
        // ponctualité = 40 × payées/(payées+retards) : 40 → 26,67 → perte de ~13 pts
        assertThat(sansRetard - avecRetards).isEqualTo(13);
    }

    @Test
    void cycles_jamais_rattrapes_penalisent_le_score() {
        // Ancien bug : un membre qui ne rattrapait jamais ses cycles ratés n'avait
        // aucune cotisation en retard → score neutre. Désormais nombreRetards compte.
        int fantome = service.calculerScore(stats(1, 0, 3, 0, 6));
        assertThat(fantome).isLessThan(40);   // bien en dessous du score neutre
    }

    @Test
    void chaque_litige_retire_10_points() {
        int sansLitige = service.calculerScore(stats(1, 12, 0, 0, 6));
        int unLitige = service.calculerScore(stats(1, 12, 0, 1, 6));
        int troisLitiges = service.calculerScore(stats(1, 12, 0, 3, 6));
        assertThat(sansLitige - unLitige).isEqualTo(10);
        // le malus litiges est plafonné à 20 pts
        assertThat(sansLitige - troisLitiges).isEqualTo(20);
    }

    @Test
    void score_reste_borne_entre_0_et_100() {
        int pire = service.calculerScore(stats(1, 5, 5, 5, 0));
        assertThat(pire).isBetween(0, 100);
    }

    @Test
    void risque_prochain_cycle_membre_fiable() {
        // Aucun retard, 1 tontine, score élevé → risque FAIBLE
        StatsMembre s = stats(1, 20, 0, 0, 18);
        int score = service.calculerScore(s);
        assertThat(ScoreFiabiliteServiceImpl.calculerRisque(s, score)).isEqualTo("FAIBLE");
    }

    @Test
    void risque_prochain_cycle_membre_fragile() {
        // Plus de cycles ratés que payés + score FAIBLE → risque ELEVE
        StatsMembre s = stats(1, 2, 3, 0, 1);
        int score = service.calculerScore(s);
        assertThat(score).isLessThan(45);
        assertThat(ScoreFiabiliteServiceImpl.calculerRisque(s, score)).isEqualTo("ELEVE");
    }

    @Test
    void risque_augmente_avec_la_charge_de_tontines() {
        StatsMembre uneSeule = stats(1, 10, 1, 0, 6);
        StatsMembre plusieurs = stats(4, 10, 1, 0, 6);
        int s1 = service.calculerScore(uneSeule);
        int s2 = service.calculerScore(plusieurs);
        String r1 = ScoreFiabiliteServiceImpl.calculerRisque(uneSeule, s1);
        String r2 = ScoreFiabiliteServiceImpl.calculerRisque(plusieurs, s2);
        // La charge ne peut qu'aggraver, jamais améliorer
        assertThat(niveauOrdinal(r2)).isGreaterThanOrEqualTo(niveauOrdinal(r1));
    }

    private int niveauOrdinal(String risque) {
        return switch (risque) { case "ELEVE" -> 2; case "MOYEN" -> 1; default -> 0; };
    }

    @Test
    void niveaux_de_confiance() {
        assertThat(ScoreFiabiliteServiceImpl.niveauConfiance(85)).isEqualTo("ELEVE");
        assertThat(ScoreFiabiliteServiceImpl.niveauConfiance(70)).isEqualTo("ELEVE");
        assertThat(ScoreFiabiliteServiceImpl.niveauConfiance(55)).isEqualTo("MOYEN");
        assertThat(ScoreFiabiliteServiceImpl.niveauConfiance(44)).isEqualTo("FAIBLE");
    }
}
