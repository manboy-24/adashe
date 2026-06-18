package com.tontine.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessions", indexes = {
        @Index(name = "idx_session_refresh_token", columnList = "refresh_token_hash"),
        @Index(name = "idx_session_prev_refresh",  columnList = "previous_refresh_token_hash"),
        @Index(name = "idx_session_user_device",   columnList = "utilisateur_id, device_id"),
        @Index(name = "idx_session_active",        columnList = "utilisateur_id, active"),
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "previous_refresh_token_hash", length = 64)
    private String previousRefreshTokenHash;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
