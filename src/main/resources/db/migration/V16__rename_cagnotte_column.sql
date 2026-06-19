-- Aligne le nom de colonne avec l'entité MembreTontine (@Column name = "cagnotte_recue_cycle_actuel")
ALTER TABLE membres_tontine
    RENAME COLUMN a_cagnotte_sur_cycle_actuel TO cagnotte_recue_cycle_actuel;
