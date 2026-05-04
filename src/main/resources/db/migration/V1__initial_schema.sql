-- ============================================================
-- AdasheCash — Schéma initial
-- V1 : toutes les tables du domaine
-- Flyway applique ce script sur une DB vierge.
-- Sur une DB existante (dev), baseline-on-migrate=true
-- marque V1 comme déjà appliqué sans le rejouer.
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ── utilisateurs ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS utilisateurs (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    nom                         VARCHAR(255)    NOT NULL,
    prenom                      VARCHAR(255)    NOT NULL,
    telephone                   VARCHAR(255)    NOT NULL,
    email                       VARCHAR(255),
    mot_de_passe                VARCHAR(255),
    code_pin                    VARCHAR(255),
    pin_defini                  TINYINT(1)      NOT NULL DEFAULT 0,
    tentatives_pin_echouees     INT                      DEFAULT 0,
    pin_bloque_jusqu_a          DATETIME(6),
    otp_code                    VARCHAR(255),
    otp_expiration              DATETIME(6),
    otp_purpose                 VARCHAR(50),
    role                        VARCHAR(50)     NOT NULL DEFAULT 'USER',
    actif                       TINYINT(1)      NOT NULL DEFAULT 1,
    telephone_verifie           TINYINT(1)      NOT NULL DEFAULT 0,
    email_verifie               TINYINT(1)      NOT NULL DEFAULT 0,
    avatar_id                   VARCHAR(255),
    fcm_token                   VARCHAR(512),
    refresh_token               VARCHAR(64),
    refresh_token_expiration    DATETIME(6),
    previous_refresh_token_hash VARCHAR(64),
    created_at                  DATETIME(6),
    updated_at                  DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_utilisateurs_telephone (telephone),
    UNIQUE KEY uk_utilisateurs_email     (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── tontines ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tontines (
    id                   BIGINT          NOT NULL AUTO_INCREMENT,
    nom                  VARCHAR(255)    NOT NULL,
    description          VARCHAR(500),
    montant_contribution DECIMAL(15,2)   NOT NULL,
    devise               VARCHAR(10)     NOT NULL DEFAULT 'FCFA',
    frequence            VARCHAR(50)     NOT NULL,
    type_tirage          VARCHAR(50)     NOT NULL,
    statut               VARCHAR(50)     NOT NULL DEFAULT 'EN_ATTENTE',
    date_debut           DATE,
    date_prochain_cycle  DATE,
    tirage_heure         VARCHAR(5)               DEFAULT '18:00',
    cycle_actuel         INT             NOT NULL DEFAULT 1,
    nombre_max_membres   INT             NOT NULL DEFAULT 20,
    code_invitation      VARCHAR(255)    NOT NULL,
    createur_id          BIGINT          NOT NULL,
    created_at           DATETIME(6),
    updated_at           DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tontines_code_invitation (code_invitation),
    CONSTRAINT fk_tontines_createur FOREIGN KEY (createur_id) REFERENCES utilisateurs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── membres_tontine ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS membres_tontine (
    id                          BIGINT      NOT NULL AUTO_INCREMENT,
    utilisateur_id              BIGINT      NOT NULL,
    tontine_id                  BIGINT      NOT NULL,
    role_membre_tontine         VARCHAR(50) NOT NULL DEFAULT 'MEMBRE',
    ordre_tour                  INT,
    statut_membre               VARCHAR(50) NOT NULL DEFAULT 'EN_ATTENTE',
    actif                       TINYINT(1)  NOT NULL DEFAULT 0,
    a_cagnotte_sur_cycle_actuel TINYINT(1)  NOT NULL DEFAULT 0,
    nombre_retards              INT         NOT NULL DEFAULT 0,
    date_adhesion               DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_membres_utilisateur_tontine (utilisateur_id, tontine_id),
    UNIQUE KEY uk_membre_ordre_tour           (tontine_id, ordre_tour),
    CONSTRAINT fk_membres_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs   (id),
    CONSTRAINT fk_membres_tontine     FOREIGN KEY (tontine_id)     REFERENCES tontines       (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── cotisations ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cotisations (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    tontine_id            BIGINT        NOT NULL,
    membre_id             BIGINT        NOT NULL,
    montant               DECIMAL(15,2) NOT NULL,
    numero_cycle          INT           NOT NULL,
    statut                VARCHAR(50)   NOT NULL DEFAULT 'EN_ATTENTE',
    date_echeance         DATE,
    date_paiement         DATE,
    reference_transaction VARCHAR(255),
    mode_paiement         VARCHAR(100),
    est_en_retard         TINYINT(1)    NOT NULL DEFAULT 0,
    commentaire           VARCHAR(500),
    created_at            DATETIME(6),
    updated_at            DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cotisations_reference (reference_transaction),
    CONSTRAINT fk_cotisations_tontine FOREIGN KEY (tontine_id) REFERENCES tontines       (id),
    CONSTRAINT fk_cotisations_membre  FOREIGN KEY (membre_id)  REFERENCES membres_tontine (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── tirages ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tirages (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    tontine_id       BIGINT        NOT NULL,
    beneficiaire_id  BIGINT        NOT NULL,
    effectue_par_id  BIGINT,
    numero_cycle     INT           NOT NULL,
    montant_distribue DECIMAL(15,2) NOT NULL,
    methode_tirage   VARCHAR(50)   NOT NULL,
    date_tirage      DATE,
    confirme         TINYINT(1)    NOT NULL DEFAULT 0,
    commentaire      VARCHAR(500),
    created_at       DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_tirages_tontine      FOREIGN KEY (tontine_id)      REFERENCES tontines        (id),
    CONSTRAINT fk_tirages_beneficiaire FOREIGN KEY (beneficiaire_id)  REFERENCES membres_tontine (id),
    CONSTRAINT fk_tirages_effectue_par FOREIGN KEY (effectue_par_id)  REFERENCES utilisateurs    (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── paiements ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS paiements (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    cotisation_id           BIGINT,
    membre_id               BIGINT        NOT NULL,
    montant                 DECIMAL(15,2) NOT NULL,
    devise                  VARCHAR(10)   NOT NULL DEFAULT 'XAF',
    operateur               VARCHAR(50)   NOT NULL,
    statut                  VARCHAR(50)   NOT NULL DEFAULT 'EN_ATTENTE',
    reference_transaction   VARCHAR(255),
    numero_paieur           VARCHAR(50),
    message_operateur       VARCHAR(500),
    cinetpay_transaction_id VARCHAR(255),
    cinetpay_payment_url    VARCHAR(512),
    date_paiement           DATETIME(6),
    created_at              DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_paiements_reference (reference_transaction),
    CONSTRAINT fk_paiements_cotisation FOREIGN KEY (cotisation_id) REFERENCES cotisations     (id),
    CONSTRAINT fk_paiements_membre     FOREIGN KEY (membre_id)     REFERENCES membres_tontine (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── notifications ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    utilisateur_id BIGINT        NOT NULL,
    titre          VARCHAR(255)  NOT NULL,
    message        VARCHAR(1000) NOT NULL,
    type           VARCHAR(50)   NOT NULL,
    lue            TINYINT(1)    NOT NULL DEFAULT 0,
    reference_id   BIGINT,
    reference_type VARCHAR(50),
    created_at     DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_notifications_utilisateur (utilisateur_id),
    KEY idx_notifications_created_at  (created_at),
    CONSTRAINT fk_notifications_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── audit_logs ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT,
    telephone  VARCHAR(20),
    action     VARCHAR(50) NOT NULL,
    statut     VARCHAR(10) NOT NULL,
    ip_address VARCHAR(45),
    details    VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_user_id    (user_id),
    KEY idx_audit_action     (action),
    KEY idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── shedlock ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
