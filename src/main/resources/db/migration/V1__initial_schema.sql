-- ============================================================
-- AdasheCash — Schéma initial (PostgreSQL)
-- V1 : toutes les tables du domaine
-- ============================================================

-- ── utilisateurs ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS utilisateurs (
    id                          BIGSERIAL       NOT NULL,
    nom                         VARCHAR(255)    NOT NULL,
    prenom                      VARCHAR(255)    NOT NULL,
    telephone                   VARCHAR(255)    NOT NULL,
    email                       VARCHAR(255),
    mot_de_passe                VARCHAR(255),
    code_pin                    VARCHAR(255),
    pin_defini                  BOOLEAN         NOT NULL DEFAULT FALSE,
    tentatives_pin_echouees     INT                      DEFAULT 0,
    pin_bloque_jusqu_a          TIMESTAMP(6),
    otp_code                    VARCHAR(255),
    otp_expiration              TIMESTAMP(6),
    otp_purpose                 VARCHAR(50),
    role                        VARCHAR(50)     NOT NULL DEFAULT 'USER',
    actif                       BOOLEAN         NOT NULL DEFAULT TRUE,
    telephone_verifie           BOOLEAN         NOT NULL DEFAULT FALSE,
    email_verifie               BOOLEAN         NOT NULL DEFAULT FALSE,
    avatar_id                   VARCHAR(255),
    fcm_token                   VARCHAR(512),
    refresh_token               VARCHAR(64),
    refresh_token_expiration    TIMESTAMP(6),
    previous_refresh_token_hash VARCHAR(64),
    created_at                  TIMESTAMP(6),
    updated_at                  TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_utilisateurs_telephone UNIQUE (telephone),
    CONSTRAINT uk_utilisateurs_email     UNIQUE (email)
);

-- ── tontines ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tontines (
    id                   BIGSERIAL       NOT NULL,
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
    created_at           TIMESTAMP(6),
    updated_at           TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_tontines_code_invitation UNIQUE (code_invitation),
    CONSTRAINT fk_tontines_createur FOREIGN KEY (createur_id) REFERENCES utilisateurs (id)
);

-- ── membres_tontine ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS membres_tontine (
    id                          BIGSERIAL   NOT NULL,
    utilisateur_id              BIGINT      NOT NULL,
    tontine_id                  BIGINT      NOT NULL,
    role_membre_tontine         VARCHAR(50) NOT NULL DEFAULT 'MEMBRE',
    ordre_tour                  INT,
    statut_membre               VARCHAR(50) NOT NULL DEFAULT 'EN_ATTENTE',
    actif                       BOOLEAN     NOT NULL DEFAULT FALSE,
    a_cagnotte_sur_cycle_actuel BOOLEAN     NOT NULL DEFAULT FALSE,
    nombre_retards              INT         NOT NULL DEFAULT 0,
    date_adhesion               TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_membres_utilisateur_tontine UNIQUE (utilisateur_id, tontine_id),
    CONSTRAINT uk_membre_ordre_tour           UNIQUE (tontine_id, ordre_tour),
    CONSTRAINT fk_membres_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs   (id),
    CONSTRAINT fk_membres_tontine     FOREIGN KEY (tontine_id)     REFERENCES tontines       (id)
);

-- ── cotisations ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cotisations (
    id                    BIGSERIAL     NOT NULL,
    tontine_id            BIGINT        NOT NULL,
    membre_id             BIGINT        NOT NULL,
    montant               DECIMAL(15,2) NOT NULL,
    numero_cycle          INT           NOT NULL,
    statut                VARCHAR(50)   NOT NULL DEFAULT 'EN_ATTENTE',
    date_echeance         DATE,
    date_paiement         DATE,
    reference_transaction VARCHAR(255),
    mode_paiement         VARCHAR(100),
    est_en_retard         BOOLEAN       NOT NULL DEFAULT FALSE,
    commentaire           VARCHAR(500),
    created_at            TIMESTAMP(6),
    updated_at            TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_cotisations_reference UNIQUE (reference_transaction),
    CONSTRAINT fk_cotisations_tontine FOREIGN KEY (tontine_id) REFERENCES tontines       (id),
    CONSTRAINT fk_cotisations_membre  FOREIGN KEY (membre_id)  REFERENCES membres_tontine (id)
);

-- ── tirages ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tirages (
    id                BIGSERIAL     NOT NULL,
    tontine_id        BIGINT        NOT NULL,
    beneficiaire_id   BIGINT        NOT NULL,
    effectue_par_id   BIGINT,
    numero_cycle      INT           NOT NULL,
    montant_distribue DECIMAL(15,2) NOT NULL,
    methode_tirage    VARCHAR(50)   NOT NULL,
    date_tirage       DATE,
    confirme          BOOLEAN       NOT NULL DEFAULT FALSE,
    commentaire       VARCHAR(500),
    created_at        TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_tirages_tontine      FOREIGN KEY (tontine_id)      REFERENCES tontines        (id),
    CONSTRAINT fk_tirages_beneficiaire FOREIGN KEY (beneficiaire_id)  REFERENCES membres_tontine (id),
    CONSTRAINT fk_tirages_effectue_par FOREIGN KEY (effectue_par_id)  REFERENCES utilisateurs    (id)
);

-- ── paiements ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS paiements (
    id                      BIGSERIAL     NOT NULL,
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
    date_paiement           TIMESTAMP(6),
    created_at              TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_paiements_reference UNIQUE (reference_transaction),
    CONSTRAINT fk_paiements_cotisation FOREIGN KEY (cotisation_id) REFERENCES cotisations     (id),
    CONSTRAINT fk_paiements_membre     FOREIGN KEY (membre_id)     REFERENCES membres_tontine (id)
);

-- ── notifications ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id             BIGSERIAL     NOT NULL,
    utilisateur_id BIGINT        NOT NULL,
    titre          VARCHAR(255)  NOT NULL,
    message        VARCHAR(1000) NOT NULL,
    type           VARCHAR(50)   NOT NULL,
    lue            BOOLEAN       NOT NULL DEFAULT FALSE,
    reference_id   BIGINT,
    reference_type VARCHAR(50),
    created_at     TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs (id)
);

CREATE INDEX IF NOT EXISTS idx_notifications_utilisateur ON notifications (utilisateur_id);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at  ON notifications (created_at);

-- ── audit_logs ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id         BIGSERIAL   NOT NULL,
    user_id    BIGINT,
    telephone  VARCHAR(20),
    action     VARCHAR(50) NOT NULL,
    statut     VARCHAR(10) NOT NULL,
    ip_address VARCHAR(45),
    details    VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_audit_user_id    ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action     ON audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON audit_logs (created_at);

-- ── shedlock ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
