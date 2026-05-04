-- ============================================================
-- V3 : Indexes manquants sur paiements et cotisations
-- ============================================================

-- Index sur gateway_transaction_id (cinetpay_transaction_id) pour les callbacks webhook
CREATE INDEX idx_paiements_gateway_transaction_id
    ON paiements (cinetpay_transaction_id);

-- Index sur membre_id + created_at pour la query findByMembreIdOrderByCreatedAtDesc
CREATE INDEX idx_paiements_membre_created_at
    ON paiements (membre_id, created_at DESC);

-- Index sur statut pour les filtres par statut
CREATE INDEX idx_paiements_statut
    ON paiements (statut);

-- Index composite sur cotisations pour les requêtes membre + tontine + cycle
CREATE INDEX idx_cotisations_membre_tontine_cycle
    ON cotisations (membre_id, tontine_id, numero_cycle);
