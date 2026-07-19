-- V8 : wallet Mobile Money, colonnes espèces sur paiements, numeroCycle, index refresh tokens

-- ── Table comptes_wallet ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS comptes_wallet (
    id             BIGSERIAL    NOT NULL,
    utilisateur_id BIGINT       NOT NULL,
    operateur      VARCHAR(50)  NOT NULL,
    telephone      VARCHAR(20)  NULL,
    actif          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP(6) NULL,
    updated_at     TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_compte_wallet_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id),
    CONSTRAINT uk_compte_wallet_user_op     UNIQUE (utilisateur_id, operateur)
);

-- ── Nouvelles colonnes sur paiements (idempotent) ─────────────────────────────
ALTER TABLE paiements ADD COLUMN IF NOT EXISTS paye_pour_compte   BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE paiements ADD COLUMN IF NOT EXISTS mode_paiement_reel VARCHAR(50) NULL;
ALTER TABLE paiements ADD COLUMN IF NOT EXISTS admin_payeur_id    BIGINT      NULL;
ALTER TABLE paiements ADD COLUMN IF NOT EXISTS numero_cycle       INT         NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_paiement_admin_payeur'
          AND table_name = 'paiements'
    ) THEN
        ALTER TABLE paiements
            ADD CONSTRAINT fk_paiement_admin_payeur
            FOREIGN KEY (admin_payeur_id) REFERENCES utilisateurs(id);
    END IF;
END $$;

-- ── Index sur utilisateurs (refresh tokens) ──────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_utilisateur_refresh_token      ON utilisateurs(refresh_token);
CREATE INDEX IF NOT EXISTS idx_utilisateur_prev_refresh_token ON utilisateurs(previous_refresh_token_hash);
