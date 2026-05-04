-- Configuration admin : commission et numéros Mobile Money
ALTER TABLE tontines ADD COLUMN commission_pourcent FLOAT NOT NULL DEFAULT 1.0;
ALTER TABLE tontines ADD COLUMN numero_mtn_momo     VARCHAR(20);
ALTER TABLE tontines ADD COLUMN numero_orange_momo  VARCHAR(20);
