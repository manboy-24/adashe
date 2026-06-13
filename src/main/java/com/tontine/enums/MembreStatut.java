package com.tontine.enums;

public enum MembreStatut {
    EN_ATTENTE, // invitation envoyée, pas encore acceptée
    ACTIF,      // membre actif dans la tontine
    RETIRE,     // retiré ou invitation annulée
    BLOQUE      // bloqué automatiquement après 2 retards consécutifs
}
