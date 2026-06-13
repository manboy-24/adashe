-- V9 : Commission prélevée sur tirage + statut BLOQUÉ membre

-- 1. Colonne commission_prelevee sur les tirages (défaut 0 pour compatibilité données existantes)
ALTER TABLE tirages
    ADD COLUMN commission_prelevee DECIMAL(15,2) NOT NULL DEFAULT 0.00;

-- 2. Le statut BLOQUÉ est un VARCHAR(50) : aucun ALTER nécessaire (déjà EN_ATTENTE/ACTIF/RETIRE).
--    L'application peut désormais écrire 'BLOQUE' dans statut_membre sans migration SQL.
