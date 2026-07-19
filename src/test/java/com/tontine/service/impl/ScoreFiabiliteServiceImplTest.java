package com.tontine.service.impl;

import com.tontine.service.impl.ScoreFiabiliteServiceImpl.StatsMembre;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreFiabiliteServiceImplTest {

    private final ScoreFiabiliteServiceImpl service =
            new ScoreFiabiliteServiceImpl(null, null, null, null, null, null);

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
        // 5 retards sur 10 → perd la moitié des 40 pts de ponctualité
        assertThat(sansRetard - avecRetards).isEqualTo(20);
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
    void niveaux_de_confiance() {
        assertThat(ScoreFiabiliteServiceImpl.niveauConfiance(85)).isEqualTo("ELEVE");
        assertThat(ScoreFiabiliteServiceImpl.niveauConfiance(70)).isEqualTo("ELEVE");
        assertThat(ScoreFiabiliteServiceImpl.niveauConfiance(55)).isEqualTo("MOYEN");
        assertThat(ScoreFiabiliteServiceImpl.niveauConfiance(44)).isEqualTo("FAIBLE");
    }
}
