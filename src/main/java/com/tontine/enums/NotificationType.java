package com.tontine.enums;
public enum NotificationType {
    RAPPEL_COTISATION, PAIEMENT_RECU, TIRAGE_EFFECTUE, TIRAGE_BENEFICIAIRE,
    RETARD_PAIEMENT, NOUVEAU_MEMBRE, INVITATION, NOUVEAU_CYCLE, DON_CONFIRME,
    TIRAGE_SIGNALE,
    /** Le score de fiabilité d'un membre vient de passer au niveau FAIBLE. */
    SCORE_CRITIQUE,
    /** Virement Mobile Money reçu (cagnotte du gagnant, commission admin). */
    VIREMENT_RECU,
    /** Échec d'un virement Mobile Money — action requise. */
    VIREMENT_ECHEC,
    /** Le membre a été retiré d'une tontine par l'administrateur. */
    MEMBRE_RETIRE,
}
