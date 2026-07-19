-- ─────────────────────────────────────────────────────────────────────────────
-- V15 : table virements_commission
-- Trace les virements de commission prélevés à chaque tirage confirmé.
-- 2 lignes par tirage : ADMIN (3/4) et ADASHE (1/4).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE virements_commission (
    id                   BIGSERIAL     NOT NULL,
    tirage_id            BIGINT        NOT NULL,
    type_beneficiaire    VARCHAR(10)   NOT NULL,
    montant              DECIMAL(15,2) NOT NULL,
    operateur            VARCHAR(30)   NOT NULL,
    numero_beneficiaire  VARCHAR(20)   NOT NULL,
    statut               VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',
    reference_transfert  VARCHAR(100)  NULL,
    message_erreur       VARCHAR(500)  NULL,
    created_at           TIMESTAMP     NULL,
    date_virement        TIMESTAMP     NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_vc_tirage FOREIGN KEY (tirage_id) REFERENCES tirages (id)
);

CREATE INDEX idx_vc_tirage ON virements_commission (tirage_id);
CREATE INDEX idx_vc_statut ON virements_commission (statut);
