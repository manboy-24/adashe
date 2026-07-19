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

    /**
     * Aperçu du score AVANT invitation — par numéro de téléphone.
     * Permet au créateur de vérifier la fiabilité d'une personne avant de l'ajouter.
     * Réservé aux administrateurs de la tontine (garde anti-consultation anonyme).
     *
     * @param tontineId   tontine dans laquelle l'invitation est envisagée
     * @param telephone   numéro du compte à évaluer (même format que l'invitation)
     * @param demandeurId utilisateur qui consulte (transmis par le contrôleur — invariant CLAUDE.md)
     */
    ScoreFiabiliteResponse getScorePreview(Long tontineId, String telephone, Long demandeurId);
}
