package com.tontine.util;

/**
 * Version courante du contrat admin.
 *
 * Historique :
 *  v1 — lancement initial (cotisations, amendes, commission)
 *  v2 — flux complet livré : fenêtre 15 min, renégociation, signalement,
 *        confirmation sécurisée par PIN, découpage >1M FCFA
 *  v3 — wallet Mobile Money requis avant démarrage ; split de commission :
 *        3/4 reversé à l'admin, 1/4 reversé à Adashe
 *
 * Incrémenter cette constante force tous les admins à ré-accepter.
 */
public final class ContratAdminVersion {

    public static final int ACTUELLE = 3;

    /** true si la version acceptée par l'utilisateur couvre (au moins) la version courante. */
    public static boolean estAcceptee(Integer versionAcceptee) {
        return versionAcceptee != null && versionAcceptee >= ACTUELLE;
    }

    private ContratAdminVersion() {
    }
}
