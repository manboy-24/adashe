package com.tontine.dto.response;
import com.tontine.enums.LitigeStatut;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TirageLitigeResponse {
    private Long id;
    private Long tirageId;
    private Long signaleParId;
    private String signaleParNom;
    private String motif;
    private LitigeStatut statut;
    private String resoluParNom;
    private LocalDateTime resoluLe;
    private String resolutionCommentaire;
    private LocalDateTime createdAt;
}
