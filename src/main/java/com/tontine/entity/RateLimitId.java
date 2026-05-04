package com.tontine.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Data @NoArgsConstructor @AllArgsConstructor
public class RateLimitId implements Serializable {
    private String ip;
    private String endpoint;
}
