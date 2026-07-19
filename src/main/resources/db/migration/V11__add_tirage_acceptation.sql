-- V11 : fenêtre de réponse du gagnant (15 min) avant confirmation du tirage

ALTER TABLE tirages
    ADD COLUMN statut_acceptation     VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE',
    ADD COLUMN date_expiration_reponse TIMESTAMP NULL;

-- Les tirages déjà confirmés avant cette migration sont historiques :
-- on les considère acceptés pour ne pas les bloquer rétroactivement.
UPDATE tirages SET statut_acceptation = 'ACCEPTE' WHERE confirme = true;
