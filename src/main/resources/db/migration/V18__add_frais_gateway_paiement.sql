ALTER TABLE paiements
    ADD COLUMN frais_gateway DECIMAL(15, 2) NOT NULL DEFAULT 0.00
        COMMENT 'Frais Monetbil prélevés sur la transaction (commission + TTA)';
