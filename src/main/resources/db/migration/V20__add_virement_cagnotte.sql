CREATE TABLE virements_cagnotte (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tirage_id            BIGINT          NOT NULL,
    montant              DECIMAL(15,2)   NOT NULL,
    operateur            VARCHAR(30)     NOT NULL,
    numero_beneficiaire  VARCHAR(20)     NOT NULL,
    statut               VARCHAR(20)     NOT NULL DEFAULT 'EN_ATTENTE',
    reference_transfert  VARCHAR(100),
    message_erreur       VARCHAR(500),
    created_at           DATETIME(6),
    date_virement        DATETIME,
    CONSTRAINT fk_vc_tirage FOREIGN KEY (tirage_id) REFERENCES tirages(id)
);
