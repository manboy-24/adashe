-- V13 : signalement/contestation d'un tirage (par n'importe quel membre)

ALTER TABLE tirages
    ADD COLUMN en_litige BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS tirage_litiges (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    tirage_id               BIGINT       NOT NULL,
    signale_par_id          BIGINT       NOT NULL,
    motif                   VARCHAR(500) NOT NULL,
    statut                  VARCHAR(20)  NOT NULL DEFAULT 'EN_COURS',
    resolu_par_id           BIGINT       NULL,
    resolu_le               DATETIME     NULL,
    resolution_commentaire  VARCHAR(500) NULL,
    created_at              DATETIME     NOT NULL,
    CONSTRAINT fk_litige_tirage      FOREIGN KEY (tirage_id)      REFERENCES tirages(id),
    CONSTRAINT fk_litige_signale_par FOREIGN KEY (signale_par_id) REFERENCES utilisateurs(id),
    CONSTRAINT fk_litige_resolu_par  FOREIGN KEY (resolu_par_id)  REFERENCES utilisateurs(id)
);

CREATE INDEX idx_litige_tirage ON tirage_litiges(tirage_id);
