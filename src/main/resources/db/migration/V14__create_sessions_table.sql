CREATE TABLE sessions (
    id                          BIGSERIAL       NOT NULL,
    utilisateur_id              BIGINT          NOT NULL,
    refresh_token_hash          VARCHAR(64)     NOT NULL,
    previous_refresh_token_hash VARCHAR(64),
    device_id                   VARCHAR(255)    NOT NULL,
    device_name                 VARCHAR(255),
    fcm_token                   VARCHAR(512),
    created_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at                  TIMESTAMP       NOT NULL,
    active                      BOOLEAN         NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    CONSTRAINT fk_session_utilisateur
        FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE CASCADE
);

CREATE INDEX idx_session_refresh_token ON sessions (refresh_token_hash);
CREATE INDEX idx_session_prev_refresh  ON sessions (previous_refresh_token_hash);
CREATE INDEX idx_session_user_device   ON sessions (utilisateur_id, device_id);
CREATE INDEX idx_session_active        ON sessions (utilisateur_id, active);
