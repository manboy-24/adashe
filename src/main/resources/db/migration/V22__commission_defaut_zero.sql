-- Commission par défaut : 0% — l'admin doit la configurer explicitement avant le démarrage.
-- La part Adashe (1/4) est supprimée : 100% de la commission va à l'admin.
ALTER TABLE tontines ALTER COLUMN commission_pourcent SET DEFAULT 0.0;

-- Remise à zéro des tontines existantes créées avec l'ancien défaut de 1%
UPDATE tontines SET commission_pourcent = 0.0 WHERE commission_pourcent = 1.0;
