package com.tontine.enums;

/**
 * Statut de la fenêtre de réponse du gagnant (15 minutes après le tirage) :
 * - EN_ATTENTE : le gagnant n'a pas encore répondu, le délai n'est pas écoulé.
 * - ACCEPTE    : le gagnant a accepté (explicitement, ou implicitement après
 *                expiration du délai sans réponse — silence = acceptation).
 * - DECLINE    : le gagnant a refusé de recevoir la cagnotte ce cycle ; un
 *                remplaçant doit être choisi (voir choisirRemplacant).
 */
public enum TirageAcceptationStatut {
    EN_ATTENTE,
    ACCEPTE,
    DECLINE
}
