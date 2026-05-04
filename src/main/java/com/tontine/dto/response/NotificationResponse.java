package com.tontine.dto.response;
import com.tontine.enums.NotificationType;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String titre;
    private String message;
    private NotificationType type;
    private Boolean lue;
    private Long referenceId;
    private String referenceType;
    private LocalDateTime createdAt;
}
