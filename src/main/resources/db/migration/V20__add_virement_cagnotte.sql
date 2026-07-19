CREATE TABLE IF NOT EXISTS virements_cagnotte (
    id                   BIGSERIAL       PRIMARY KEY,
    tirage_id            BIGINT          NOT NULL,
    montant              DECIMAL(15,2)   NOT NULL,
    operateur            VARCHAR(30)     NOT NULL,
    numero_beneficiaire  VARCHAR(20)     NOT NULL,
    statut               VARCHAR(20)     NOT NULL DEFAULT 'EN_ATTENTE',
    reference_transfert  VARCHAR(100),
    message_erreur       VARCHAR(500),
    created_at           TIMESTAMP(6),
    date_virement        TIMESTAMP,
    CONSTRAINT fk_virement_cagnotte_tirage FOREIGN KEY (tirage_id) REFERENCES tirages(id)
);
