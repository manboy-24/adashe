package com.tontine.util;

/**
 * Version courante du contrat admin (conditions de commission, amende et
 * garde des fonds) que tout créateur de tontine doit accepter avant de
 * pouvoir en créer une.
 *
 * Incrémenter cette constante force tous les admins à ré-accepter — à faire
 * dès que le contenu du contrat change de façon substantielle (ex: une fois
 * le transfert automatique de la cagnotte réellement implémenté).
 */
public final class ContratAdminVersion {

    public static final int ACTUELLE = 1;

    /** true si la version acceptée par l'utilisateur couvre (au moins) la version courante. */
    public static boolean estAcceptee(Integer versionAcceptee) {
        return versionAcceptee != null && versionAcceptee >= ACTUELLE;
    }

    private ContratAdminVersion() {
    }
}
