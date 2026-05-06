-- V8 : wallet Mobile Money, colonnes espèces sur paiements, numeroCycle, index refresh tokens

-- ── Table comptes_wallet ──────────────────────────────────────────────────────
CREATE TABLE comptes_wallet (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    utilisateur_id BIGINT       NOT NULL,
    operateur      VARCHAR(50)  NOT NULL,
    telephone      VARCHAR(20)  NULL,
    actif          TINYINT(1)   NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NULL,
    updated_at     DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_compte_wallet_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id),
    CONSTRAINT uk_compte_wallet_user_op     UNIQUE (utilisateur_id, operateur)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Nouvelles colonnes sur paiements ─────────────────────────────────────────
ALTER TABLE paiements
    ADD COLUMN paye_pour_compte   TINYINT(1)  NOT NULL DEFAULT 0
        COMMENT 'true = admin a payé en MoMo pour le compte d''un membre',
    ADD COLUMN mode_paiement_reel VARCHAR(50) NULL
        COMMENT 'Opérateur réel (MTN/Orange) quand operateur=ESPECES',
    ADD COLUMN admin_payeur_id    BIGINT      NULL
        COMMENT 'Admin qui a effectué le virement pour le membre',
    ADD COLUMN numero_cycle       INT         NULL
        COMMENT 'Cycle ciblé par ce paiement (rattrapage ou cycle courant)',
    ADD CONSTRAINT fk_paiement_admin_payeur
        FOREIGN KEY (admin_payeur_id) REFERENCES utilisateurs(id);

-- ── Index sur utilisateurs (refresh tokens) ──────────────────────────────────
CREATE INDEX idx_utilisateur_refresh_token
    ON utilisateurs(refresh_token);

CREATE INDEX idx_utilisateur_prev_refresh_token
    ON utilisateurs(previous_refresh_token_hash);
