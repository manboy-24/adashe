package com.tontine.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class AuditLogResponse {
    private Long id;
    private Long userId;
    private String telephone;
    private String action;
    private String statut;
    private String ipAddress;
    private String details;
    private LocalDateTime createdAt;
}
