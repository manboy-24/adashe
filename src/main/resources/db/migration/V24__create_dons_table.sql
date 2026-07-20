-- V24 : table des dons (Mobile Money via Monetbil placePayment)
-- L'entité Don existait mais aucune migration ne créait la table → 500 « relation "dons" does not exist ».

CREATE TABLE dons (
    id                     BIGSERIAL     PRIMARY KEY,
    utilisateur_id         BIGINT        NOT NULL,
    montant                NUMERIC(15,2) NOT NULL,
    devise                 VARCHAR(10)   NOT NULL DEFAULT 'XAF',
    operateur              VARCHAR(50)   NOT NULL,
    statut                 VARCHAR(50)   NOT NULL DEFAULT 'EN_ATTENTE',
    reference_transaction  VARCHAR(255)  UNIQUE,
    numero_paieur          VARCHAR(255),
    message_operateur      VARCHAR(500),
    gateway_transaction_id VARCHAR(255),
    gateway_payment_url    VARCHAR(255),
    tirage_id              BIGINT        NULL,
    created_at             TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_don_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs (id),
    CONSTRAINT fk_don_tirage      FOREIGN KEY (tirage_id)      REFERENCES tirages (id)
);

CREATE INDEX idx_don_utilisateur    ON dons (utilisateur_id);
CREATE INDEX idx_don_reference      ON dons (reference_transaction);
CREATE INDEX idx_don_gateway_tx     ON dons (gateway_transaction_id);
