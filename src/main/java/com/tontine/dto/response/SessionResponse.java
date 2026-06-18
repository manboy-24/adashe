package com.tontine.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionResponse {
    private Long          id;
    private String        deviceName;
    private String        deviceId;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private boolean       active;
    /** true si c'est la session courante (deviceId correspond à l'appelant). */
    private boolean       current;
}
