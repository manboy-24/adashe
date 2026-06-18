CREATE TABLE sessions (
    id                          BIGINT          NOT NULL AUTO_INCREMENT,
    utilisateur_id              BIGINT          NOT NULL,
    refresh_token_hash          VARCHAR(64)     NOT NULL,
    previous_refresh_token_hash VARCHAR(64),
    device_id                   VARCHAR(255)    NOT NULL,
    device_name                 VARCHAR(255),
    fcm_token                   VARCHAR(512),
    created_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at                  TIMESTAMP       NOT NULL,
    active                      BOOLEAN         NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    CONSTRAINT fk_session_utilisateur
        FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE CASCADE,

    INDEX idx_session_refresh_token (refresh_token_hash),
    INDEX idx_session_prev_refresh  (previous_refresh_token_hash),
    INDEX idx_session_user_device   (utilisateur_id, device_id),
    INDEX idx_session_active        (utilisateur_id, active)
);
