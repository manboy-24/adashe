-- ============================================================
-- V2 : Soft-delete sur la table tontines
-- Ajoute la colonne deleted_at (NULL = actif, non-NULL = supprimé)
-- et un index partiel pour ne pas pénaliser les lectures courantes.
-- ============================================================

ALTER TABLE tontines
    ADD COLUMN deleted_at DATETIME(6) NULL DEFAULT NULL
        COMMENT 'NULL = actif ; non-NULL = supprimé (soft-delete)';

-- Index pour accélérer la clause WHERE deleted_at IS NULL
-- systématiquement appliquée par @SQLRestriction sur l'entité.
CREATE INDEX idx_tontines_deleted_at ON tontines (deleted_at);
