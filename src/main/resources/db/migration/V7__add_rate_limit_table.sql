-- Rate limiting persistant : survit aux redémarrages, fonctionne sur plusieurs instances.
-- Clé primaire composite (ip, endpoint) → un seul row par IP par endpoint.
-- L'UPSERT MySQL (ON DUPLICATE KEY UPDATE) garantit l'atomicité sans verrou applicatif.

CREATE TABLE rate_limit (
    ip           VARCHAR(45)  NOT NULL COMMENT 'Adresse IP du client (IPv4 ou IPv6)',
    endpoint     VARCHAR(50)  NOT NULL COMMENT 'Identifiant court de l''endpoint (ex: OTP, PIN, PAIEMENT)',
    hits         INT          NOT NULL DEFAULT 1 COMMENT 'Nombre de requêtes dans la fenêtre courante',
    window_start DATETIME(3)  NOT NULL COMMENT 'Début de la fenêtre glissante (précision milliseconde)',
    PRIMARY KEY (ip, endpoint)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='Compteurs de rate limiting persistants par IP et endpoint';
