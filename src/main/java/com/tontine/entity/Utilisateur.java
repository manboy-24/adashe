package com.tontine.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tontine.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "utilisateurs")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(unique = true, nullable = false)
    private String telephone;

    @Column(unique = true)
    private String email;

    // Mot de passe (optionnel — auth par PIN)
    @JsonIgnore
    private String motDePasse;

    // ── PIN 4 chiffres (hashé BCrypt) ──────────────────────────────────────
    @JsonIgnore
    private String codePin;

    @Column(nullable = false)
    @Builder.Default
    private Boolean pinDefini = false;

    @JsonIgnore
    @Builder.Default
    private Integer tentativesPinEchouees = 0;

    @JsonIgnore
    private LocalDateTime pinBloqueJusquA;

    // ── OTP (inscription & reset PIN) ───────────────────────────────────────
    @JsonIgnore
    private String otpCode;
    @JsonIgnore
    private LocalDateTime otpExpiration;
    @JsonIgnore
    private String otpPurpose;   // "INSCRIPTION" | "RESET_PIN"

    // ── Rôle & statut ───────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    @Column(nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean telephoneVerifie = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerifie = false;

    // ── Avatar (ID du drawable partagé avec l'app mobile) ───────────────────
    private String avatarId;

    // ── Firebase Cloud Messaging ─────────────────────────────────────────────
    @JsonIgnore
    private String fcmToken;

    // ── Refresh token JWT ────────────────────────────────────────────────────
    @JsonIgnore
    private String refreshToken;
    @JsonIgnore
    private LocalDateTime refreshTokenExpiration;
    @JsonIgnore
    private String previousRefreshTokenHash;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // ── Relations ────────────────────────────────────────────────────────────
    @OneToMany(mappedBy = "createur", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Tontine> tontinesCreees = new ArrayList<>();

    @OneToMany(mappedBy = "utilisateur", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MembreTontine> participations = new ArrayList<>();

    @OneToMany(mappedBy = "utilisateur", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Notification> notifications = new ArrayList<>();

    // ── Méthodes utilitaires ─────────────────────────────────────────────────
    public boolean estPinBloque() {
        return pinBloqueJusquA != null && LocalDateTime.now().isBefore(pinBloqueJusquA);
    }
}
