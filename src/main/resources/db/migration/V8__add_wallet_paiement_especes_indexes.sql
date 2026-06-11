-- V8 : wallet Mobile Money, colonnes espèces sur paiements, numeroCycle, index refresh tokens

-- ── Table comptes_wallet ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS comptes_wallet (
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

-- ── Nouvelles colonnes sur paiements (idempotent) ─────────────────────────────
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paiements' AND COLUMN_NAME = 'paye_pour_compte');
SET @sql = IF(@col = 0,
    "ALTER TABLE paiements ADD COLUMN paye_pour_compte TINYINT(1) NOT NULL DEFAULT 0",
    "SELECT 'paye_pour_compte already exists'");
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paiements' AND COLUMN_NAME = 'mode_paiement_reel');
SET @sql = IF(@col = 0,
    "ALTER TABLE paiements ADD COLUMN mode_paiement_reel VARCHAR(50) NULL",
    "SELECT 'mode_paiement_reel already exists'");
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paiements' AND COLUMN_NAME = 'admin_payeur_id');
SET @sql = IF(@col = 0,
    "ALTER TABLE paiements ADD COLUMN admin_payeur_id BIGINT NULL",
    "SELECT 'admin_payeur_id already exists'");
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paiements' AND COLUMN_NAME = 'numero_cycle');
SET @sql = IF(@col = 0,
    "ALTER TABLE paiements ADD COLUMN numero_cycle INT NULL",
    "SELECT 'numero_cycle already exists'");
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Add FK only if admin_payeur_id has no FK referencing utilisateurs yet
SET @fk = (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'paiements'
      AND COLUMN_NAME = 'admin_payeur_id' AND REFERENCED_TABLE_NAME = 'utilisateurs');
SET @sql = IF(@fk = 0,
    "ALTER TABLE paiements ADD CONSTRAINT fk_paiement_admin_payeur FOREIGN KEY (admin_payeur_id) REFERENCES utilisateurs(id)",
    "SELECT 'fk_paiement_admin_payeur already exists'");
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── Index sur utilisateurs (refresh tokens) ──────────────────────────────────
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'utilisateurs' AND INDEX_NAME = 'idx_utilisateur_refresh_token');
SET @sql = IF(@idx = 0,
    "CREATE INDEX idx_utilisateur_refresh_token ON utilisateurs(refresh_token)",
    "SELECT 'idx_utilisateur_refresh_token already exists'");
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'utilisateurs' AND INDEX_NAME = 'idx_utilisateur_prev_refresh_token');
SET @sql = IF(@idx = 0,
    "CREATE INDEX idx_utilisateur_prev_refresh_token ON utilisateurs(previous_refresh_token_hash)",
    "SELECT 'idx_utilisateur_prev_refresh_token already exists'");
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
