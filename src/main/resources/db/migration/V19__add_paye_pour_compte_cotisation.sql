ALTER TABLE cotisations
    ADD COLUMN paye_pour_compte TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '1 si admin a payé pour le membre (espèces remises à l admin)';
