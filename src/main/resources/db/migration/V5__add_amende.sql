-- Montant de l'amende pour cycle non payé (configurable par l'admin, défaut 200 FCFA)
ALTER TABLE tontines ADD COLUMN montant_amende BIGINT NOT NULL DEFAULT 200;

-- Montant de l'amende prélevée sur chaque cotisation (0 = paiement normal, >0 = rattrapage)
ALTER TABLE cotisations ADD COLUMN montant_amende BIGINT NOT NULL DEFAULT 0;
