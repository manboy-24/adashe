-- V10 : Adashe Score — cache persistant du score de fiabilité IA par utilisateur

CREATE TABLE scores_fiabilite (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id  BIGINT       NOT NULL,
    score           INT          NOT NULL,
    niveau_confiance VARCHAR(20) NOT NULL,
    explication     TEXT         NULL,
    recommandation  VARCHAR(500) NULL,
    donnees_hash    VARCHAR(64)  NOT NULL,
    modele_ia       VARCHAR(50)  NULL,
    created_at      DATETIME(6)  NULL,
    updated_at      DATETIME(6)  NULL,
    CONSTRAINT uk_score_utilisateur UNIQUE (utilisateur_id),
    CONSTRAINT fk_score_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs (id)
);
