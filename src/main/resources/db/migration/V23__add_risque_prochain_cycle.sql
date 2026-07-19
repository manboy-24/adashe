-- Score prédictif : risque estimé de retard au prochain cycle (FAIBLE | MOYEN | ELEVE)
ALTER TABLE scores_fiabilite ADD COLUMN risque_prochain_cycle VARCHAR(10);
