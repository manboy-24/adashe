package com.tontine.service;

import com.tontine.dto.response.ScoreFiabiliteResponse;

public interface ScoreFiabiliteService {

    /**
     * Adashe Score — score de fiabilité communautaire d'un membre.
     * Réservé aux administrateurs (créateur) de la tontine.
     *
     * @param tontineId   tontine dans laquelle le score est consulté
     * @param membreId    membre (MembreTontine) évalué
     * @param demandeurId utilisateur qui consulte (transmis par le contrôleur — invariant CLAUDE.md)
     */
    ScoreFiabiliteResponse getScoreMembre(Long tontineId, Long membreId, Long demandeurId);
}
