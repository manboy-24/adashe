-- Montant de l'amende inclus dans ce paiement Mobile Money (0 = paiement normal)
ALTER TABLE paiements ADD COLUMN montant_amende BIGINT NOT NULL DEFAULT 0;

-- Suivi des virements d'amende vers le compte du développeur
CREATE TABLE virements_amende (
    id                  BIGSERIAL      PRIMARY KEY,
    paiement_id         BIGINT         NOT NULL,
    montant             BIGINT         NOT NULL,
    operateur           VARCHAR(30)    NOT NULL,
    numero_beneficiaire VARCHAR(20)    NOT NULL,
    reference_tontine   VARCHAR(50)    NOT NULL,
    statut              VARCHAR(20)    NOT NULL DEFAULT 'EN_ATTENTE',
    reference_transfert VARCHAR(100),
    message_erreur      VARCHAR(500),
    created_at          TIMESTAMP(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_virement       TIMESTAMP(6),
    CONSTRAINT fk_virement_paiement FOREIGN KEY (paiement_id) REFERENCES paiements(id)
);

CREATE INDEX idx_virements_amende_statut ON virements_amende (statut);
