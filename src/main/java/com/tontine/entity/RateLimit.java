package com.tontine.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "rate_limit")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RateLimit {

    @EmbeddedId
    private RateLimitId id;

    @Column(nullable = false)
    private int hits;

    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;
}
