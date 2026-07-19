-- Rate limiting persistant : survit aux redémarrages, fonctionne sur plusieurs instances.
-- Clé primaire composite (ip, endpoint) → un seul row par IP par endpoint.
-- L'UPSERT PostgreSQL (ON CONFLICT DO UPDATE) garantit l'atomicité sans verrou applicatif.

CREATE TABLE rate_limit (
    ip           VARCHAR(45)  NOT NULL,
    endpoint     VARCHAR(50)  NOT NULL,
    hits         INT          NOT NULL DEFAULT 1,
    window_start TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (ip, endpoint)
);
