-- V12 : renégociation après déclin — liste des membres intéressés par cycle

CREATE TABLE IF NOT EXISTS tirage_interets (
    id           BIGSERIAL PRIMARY KEY,
    tontine_id   BIGINT  NOT NULL,
    numero_cycle INT     NOT NULL,
    membre_id    BIGINT  NOT NULL,
    created_at   TIMESTAMP NOT NULL,
    CONSTRAINT fk_tirage_interet_tontine FOREIGN KEY (tontine_id) REFERENCES tontines(id),
    CONSTRAINT fk_tirage_interet_membre  FOREIGN KEY (membre_id)  REFERENCES membres_tontine(id),
    CONSTRAINT uq_tirage_interet UNIQUE (tontine_id, numero_cycle, membre_id)
);

CREATE INDEX idx_tirage_interet_tontine_cycle ON tirage_interets(tontine_id, numero_cycle);
