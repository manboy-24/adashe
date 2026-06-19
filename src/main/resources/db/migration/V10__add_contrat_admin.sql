-- V10 : Acceptation du contrat admin (avant création d'une tontine)

-- NULL = jamais accepté. Comparé à ContratAdminVersion.ACTUELLE pour savoir
-- si l'utilisateur doit ré-accepter (ex: après une mise à jour des conditions).
ALTER TABLE utilisateurs
    ADD COLUMN contrat_admin_version     INT      NULL,
    ADD COLUMN contrat_admin_accepte_le  DATETIME NULL;
