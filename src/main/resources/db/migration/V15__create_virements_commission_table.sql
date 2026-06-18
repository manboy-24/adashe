-- ─────────────────────────────────────────────────────────────────────────────
-- V15 : table virements_commission
-- Trace les virements de commission prélevés à chaque tirage confirmé.
-- 2 lignes par tirage : ADMIN (3/4) et ADASHE (1/4).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE virements_commission (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    tirage_id            BIGINT       NOT NULL,
    type_beneficiaire    VARCHAR(10)  NOT NULL COMMENT 'ADMIN ou ADASHE',
    montant              DECIMAL(15,2) NOT NULL,
    operateur            VARCHAR(30)  NOT NULL,
    numero_beneficiaire  VARCHAR(20)  NOT NULL,
    statut               VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE',
    reference_transfert  VARCHAR(100) NULL,
    message_erreur       VARCHAR(500) NULL,
    created_at           DATETIME     NULL,
    date_virement        DATETIME     NULL,

    PRIMARY KEY (id),
    INDEX idx_vc_tirage   (tirage_id),
    INDEX idx_vc_statut   (statut),

    CONSTRAINT fk_vc_tirage FOREIGN KEY (tirage_id) REFERENCES tirages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
